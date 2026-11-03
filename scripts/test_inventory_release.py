#!/usr/bin/env python3
"""Saga compensation helper: releasing the same reservation twice restores capacity once."""
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


def log(message):
    print(f"[{datetime.now().isoformat(timespec='milliseconds')}] {message}", flush=True)


def request(method, path, body=None, extra_headers=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json", "X-Correlation-Id": f"release-test-{uuid.uuid4()}"}
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            return res.status, json.loads(res.read().decode())
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode())


def main():
    log(f"Starting repeated-release check against {BASE}")
    status, before = request("GET", f"/api/v1/inventory/{TICKET_TYPE_ID}")
    if status != 200:
        raise SystemExit(f"Inventory API unavailable: {status} {before}")
    if before["available_quantity"] < 1:
        raise SystemExit("No ticket available. Reset local demo DB before this test.")

    booking_id = f"release-{uuid.uuid4()}"
    body = {"booking_id": booking_id, "event_id": EVENT_ID,
            "ticket_type_id": TICKET_TYPE_ID, "quantity": 1}
    status, reservation = request("POST", "/api/v1/reservations", body,
                                  {"Idempotency-Key": f"reserve-{booking_id}"})
    assert status == 201, f"Reservation should be created: {status} {reservation}"
    reservation_id = reservation["reservation_id"]

    first_status, first = request("POST", f"/api/v1/reservations/{reservation_id}/release")
    second_status, second = request("POST", f"/api/v1/reservations/{reservation_id}/release")
    final_status, after = request("GET", f"/api/v1/inventory/{TICKET_TYPE_ID}")

    log(f"Initial available_quantity: {before['available_quantity']}")
    log(f"After reserve: {before['available_quantity'] - 1}")
    log(f"First release: HTTP {first_status}, status={first.get('status')}")
    log(f"Second release: HTTP {second_status}, status={second.get('status')}")
    log(f"After both releases: HTTP {final_status}, available_quantity={after.get('available_quantity')}")
    sql = (f"SELECT id, booking_id, status, quantity FROM reservations WHERE id = '{reservation_id}'; "
           f"SELECT ticket_type_id, total_capacity, available_quantity FROM ticket_inventory WHERE ticket_type_id = '{TICKET_TYPE_ID}';")
    command = ["docker", "exec", "-e", "PGPASSWORD=inventory_pass", "etb-postgres",
               "psql", "-h", "localhost", "-U", "inventory_user", "-d", "inventory_db", "-c", sql]
    try:
        output = subprocess.run(command, capture_output=True, text=True, check=True).stdout
        log("Final database state:\n" + output)
    except (OSError, subprocess.CalledProcessError) as exc:
        log(f"Database query skipped; API assertions still ran: {exc}")
    assert first_status == 200 and first["status"] == "RELEASED"
    assert second_status == 200 and second["status"] == "RELEASED"
    assert final_status == 200
    assert after["available_quantity"] == before["available_quantity"], "Release did not restore capacity exactly once"
    log(f"PASS: reservation_id={reservation_id}; repeated release restored capacity exactly once.")


if __name__ == "__main__":
    main()
