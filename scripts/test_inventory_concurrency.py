#!/usr/bin/env python3
"""Challenge 1: fire 20 simultaneous unique reservations at a fresh capacity of five."""
import concurrent.futures
import json
import os
import subprocess
import urllib.error
import urllib.request
import uuid
from datetime import datetime

BASE = os.getenv("INVENTORY_URL", "http://localhost:8082").rstrip("/")
EVENT_ID = os.getenv("EVENT_ID", "10000000-0000-0000-0000-000000000001")
TICKET_TYPE_ID = os.getenv("TICKET_TYPE_ID", "20000000-0000-0000-0000-000000000001")
REQUESTS = 20
EXPECTED_CAPACITY = 5
RUN_ID = uuid.uuid4().hex[:8]


def log(message):
    print(f"[{datetime.now().isoformat(timespec='milliseconds')}] {message}", flush=True)


def request(method, path, body=None, extra_headers=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {
        "Content-Type": "application/json",
        "X-Correlation-Id": f"challenge-1-{uuid.uuid4()}",
    }
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            payload = json.loads(res.read().decode()) if res.headers.get("Content-Type", "").startswith("application/json") else {}
            return res.status, payload
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode())


def reserve(_):
    booking_id = f"conc-{RUN_ID}-{uuid.uuid4().hex}"
    body = {
        "booking_id": booking_id,
        "event_id": EVENT_ID,
        "ticket_type_id": TICKET_TYPE_ID,
        "quantity": 1,
    }
    return request("POST", "/api/v1/reservations", body,
                   {"Idempotency-Key": f"reserve-{booking_id}"})


def print_database_state():
    sql = (
        f"SELECT status, COUNT(*) FROM reservations WHERE booking_id LIKE 'conc-{RUN_ID}-%' GROUP BY status ORDER BY status; "
        f"SELECT ticket_type_id, total_capacity, available_quantity FROM ticket_inventory WHERE ticket_type_id = '{TICKET_TYPE_ID}';"
    )
    command = [
        "docker", "exec", "-e", "PGPASSWORD=inventory_pass", "etb-postgres",
        "psql", "-h", "localhost", "-U", "inventory_user", "-d", "inventory_db", "-c", sql,
    ]
    try:
        output = subprocess.run(command, capture_output=True, text=True, check=True).stdout
        log("Final database state (challenge reservations and inventory row):\n" + output)
    except (OSError, subprocess.CalledProcessError) as exc:
        log(f"Database query skipped; API assertions still ran: {exc}")


def main():
    log(f"Run ID={RUN_ID}; starting concurrency test against {BASE}; expected fresh capacity={EXPECTED_CAPACITY}")
    snapshot_status, snapshot = request("GET", f"/api/v1/inventory/{TICKET_TYPE_ID}")
    if snapshot_status != 200:
        raise SystemExit(f"Inventory API unavailable: HTTP {snapshot_status}: {snapshot}")
    if snapshot.get("available_quantity") != EXPECTED_CAPACITY:
        raise SystemExit(
            f"Expected a fresh seeded inventory of {EXPECTED_CAPACITY}, got {snapshot.get('available_quantity')}. "
            "Reset the local demo DB first (docker compose down -v, then docker compose up -d)."
        )

    with concurrent.futures.ThreadPoolExecutor(max_workers=REQUESTS) as pool:
        results = list(pool.map(reserve, range(REQUESTS)))
    successes = [body for status, body in results if status == 201]
    replays = [body for status, body in results if status == 200]
    failures = [(status, body) for status, body in results if status not in (200, 201)]
    capacity_errors = [item for item in failures if item[1].get("code") == "INSUFFICIENT_INVENTORY"]

    final_status, final = request("GET", f"/api/v1/inventory/{TICKET_TYPE_ID}")
    log(f"Requests sent: {REQUESTS}")
    log(f"New reservations (201): {len(successes)}")
    log(f"Idempotent replays (200): {len(replays)}")
    log(f"Rejected for insufficient inventory: {len(capacity_errors)}")
    log(f"Unexpected responses: {len(failures) - len(capacity_errors)}")
    log(f"Final inventory HTTP {final_status}: {json.dumps(final, sort_keys=True)}")
    print_database_state()

    assert final_status == 200, "Could not retrieve final inventory"
    assert len(successes) == EXPECTED_CAPACITY, f"Expected {EXPECTED_CAPACITY} successes"
    assert len(replays) == 0, "Concurrency test uses unique keys; unexpected replay"
    assert len(capacity_errors) == REQUESTS - EXPECTED_CAPACITY, "Unexpected capacity rejection count"
    assert len(failures) == len(capacity_errors), "Some requests failed for a non-capacity reason"
    assert final.get("available_quantity") == 0, "Inventory must be exhausted but never negative"
    log("PASS: concurrent requests never over-allocated inventory.")


if __name__ == "__main__":
    main()
