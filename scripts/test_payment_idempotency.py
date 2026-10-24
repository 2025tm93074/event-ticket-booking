#!/usr/bin/env python3
"""
Payment idempotency checks against a running payment-mock service (Challenge 2, payment side).

Scenarios:
  1. Sequential repeat      - same key + same payload -> 201 then 200 replay, one payment id
  2. Payload conflict       - same key + different payload -> 409 IDEMPOTENCY_KEY_REUSED
  3. Simultaneous duplicates - N parallel requests with one key -> exactly one 201, rest 200, one payment id
  4. Duplicates while slow  - parallel duplicates while the first charge is still PROCESSING
  5. Second key, same booking - 409 BOOKING_ALREADY_PAID (no double charge for one booking)
  6. Declined payment       - FAIL mode returns FAILED, replay returns FAILED, new key after decline succeeds
  7. Database check         - exactly one row per idempotency key

Usage:
  python scripts/test_payment_idempotency.py [--base-url http://localhost:8084] [--parallel 20]
Uses only the Python standard library. The database check needs docker (container etb-postgres).
"""
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

RESULTS = []


def log(message):
    print(f"[{datetime.now().isoformat(timespec='milliseconds')}] {message}", flush=True)


def call(method, url, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, dict(response.headers), json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), json.loads(error.read() or b"null")


def check(name, condition, detail=""):
    RESULTS.append((name, condition))
    log(f"{'PASS' if condition else 'FAIL'}  {name}  {detail}")


def pay(base, key, booking_id, amount="1500.00", correlation_id=None):
    headers = {"Idempotency-Key": key, "X-Correlation-Id": correlation_id or f"idem-test-{uuid.uuid4().hex[:8]}"}
    return call("POST", f"{base}/api/v1/payments",
                {"booking_id": booking_id, "amount": amount, "currency": "INR"}, headers)


def set_mode(base, mode, delay_ms=None):
    body = {"mode": mode}
    if delay_ms is not None:
        body["delay_ms"] = delay_ms
    status, _, settings = call("PUT", f"{base}/api/v1/admin/simulation", body)
    log(f"simulation -> {settings} (HTTP {status})")


def parallel(base, count, key, booking_id):
    with ThreadPoolExecutor(max_workers=count) as pool:
        futures = [pool.submit(pay, base, key, booking_id, "1500.00", f"parallel-{i}") for i in range(count)]
        return [f.result() for f in futures]


def summarize(responses):
    statuses = {}
    for status, _, _ in responses:
        statuses[status] = statuses.get(status, 0) + 1
    ids = {body.get("payment_id") for status, _, body in responses if status in (200, 201)}
    return statuses, ids


def db_rows_per_key(keys):
    key_list = ",".join(f"'{k}'" for k in keys)
    sql = (f"SELECT idempotency_key, count(*), min(status) FROM payments "
           f"WHERE idempotency_key IN ({key_list}) GROUP BY idempotency_key ORDER BY 1;")
    cmd = ["docker", "exec", "-e", "PGPASSWORD=payment_pass", "etb-postgres",
           "psql", "-h", "localhost", "-U", "payment_user", "-d", "payment_db", "-c", sql]
    try:
        return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        return f"database check skipped: {error}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8084")
    parser.add_argument("--parallel", type=int, default=20)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    run = uuid.uuid4().hex[:8]
    log(f"run id {run}, target {base}")
    set_mode(base, "SUCCESS")

    # 1. Sequential repeat
    key, booking = f"seq-{run}", f"booking-seq-{run}"
    first = pay(base, key, booking)
    second = pay(base, key, booking)
    log(f"first : HTTP {first[0]} replay={first[1].get('Idempotent-Replay')} body={first[2]}")
    log(f"repeat: HTTP {second[0]} replay={second[1].get('Idempotent-Replay')} body={second[2]}")
    check("sequential: first call creates (201)", first[0] == 201)
    check("sequential: repeat is a replay (200, Idempotent-Replay=true)",
          second[0] == 200 and second[1].get("Idempotent-Replay") == "true")
    check("sequential: same payment id and identical body", first[2] == second[2])

    # 2. Same key, different payload
    conflict = pay(base, key, booking, amount="999.00")
    log(f"different payload: HTTP {conflict[0]} body={conflict[2]}")
    check("conflict: same key + different payload -> 409 IDEMPOTENCY_KEY_REUSED",
          conflict[0] == 409 and conflict[2].get("code") == "IDEMPOTENCY_KEY_REUSED")

    # 3. Simultaneous duplicates
    par_key, par_booking = f"par-{run}", f"booking-par-{run}"
    statuses, ids = summarize(parallel(base, args.parallel, par_key, par_booking))
    log(f"{args.parallel} parallel identical requests -> status counts {statuses}, distinct payment ids {ids}")
    check("parallel: exactly one 201", statuses.get(201) == 1)
    check(f"parallel: remaining {args.parallel - 1} are 200 replays", statuses.get(200) == args.parallel - 1)
    check("parallel: all responses carry one payment id", len(ids) == 1)

    # 4. Duplicates while the first charge is still processing
    set_mode(base, "SLOW", 2000)
    slow_key, slow_booking = f"slow-{run}", f"booking-slow-{run}"
    statuses, ids = summarize(parallel(base, 10, slow_key, slow_booking))
    log(f"10 parallel requests in SLOW mode -> status counts {statuses}, distinct payment ids {ids}")
    check("slow: one charge, duplicates wait and replay it", statuses.get(201) == 1 and len(ids) == 1)
    set_mode(base, "SUCCESS")

    # 5. Second key for an already-paid booking
    other = pay(base, f"other-{run}", booking)
    log(f"new key for already-paid booking: HTTP {other[0]} body={other[2]}")
    check("booking: second key for paid booking -> 409 BOOKING_ALREADY_PAID",
          other[0] == 409 and other[2].get("code") == "BOOKING_ALREADY_PAID")

    # 6. Declined payment, replay, retry after decline
    set_mode(base, "FAIL")
    fail_key, fail_booking = f"fail-{run}", f"booking-fail-{run}"
    declined = pay(base, fail_key, fail_booking)
    declined_again = pay(base, fail_key, fail_booking)
    log(f"FAIL mode: HTTP {declined[0]} body={declined[2]}")
    log(f"FAIL replay: HTTP {declined_again[0]} body={declined_again[2]}")
    check("decline: recorded as FAILED (201)", declined[0] == 201 and declined[2].get("status") == "FAILED")
    check("decline: replay returns the same FAILED result", declined_again[0] == 200
          and declined_again[2] == declined[2])
    set_mode(base, "SUCCESS")
    retry = pay(base, f"fail-retry-{run}", fail_booking)
    log(f"retry with new key after decline: HTTP {retry[0]} body={retry[2]}")
    check("decline: new key after a decline can succeed", retry[0] == 201 and retry[2].get("status") == "SUCCEEDED")

    # 7. Database state
    keys = [key, par_key, slow_key, fail_key, f"fail-retry-{run}", f"other-{run}"]
    rows = db_rows_per_key(keys)
    log("database rows per idempotency key:\n" + rows)
    if not rows.startswith("database check skipped"):
        counts = [line.split("|")[1].strip() for line in rows.splitlines() if f"-{run}" in line]
        check("database: exactly one row per idempotency key", counts and all(c == "1" for c in counts),
              f"{len(counts)} keys")

    passed = sum(1 for _, ok in RESULTS if ok)
    log(f"SUMMARY: {passed}/{len(RESULTS)} checks passed")
    sys.exit(0 if passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
