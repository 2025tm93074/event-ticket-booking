# Shreya Service Integration Contract

This document defines the API contract for Gairik's Booking Service and Mudit's Payment Service to integrate with Shreya's Event Catalog and Inventory services.

## Service URLs

- Catalog: `http://localhost:8081`
- Inventory: `http://localhost:8082`
- Booking: `http://localhost:8083`
- Payment mock: `http://localhost:8084`

## Stable demo data

- Event ID: `10000000-0000-0000-0000-000000000001`
- General ticket type ID: `20000000-0000-0000-0000-000000000001` (capacity 5)
- VIP ticket type ID: `20000000-0000-0000-0000-000000000002` (capacity 2)

Flyway inserts these in independent service databases on first startup. IDs are references only; there is no cross-service foreign key or database access.

## Catalog validation

`GET /api/v1/events/{event_id}`

On success returns an `EventResponse` using snake_case JSON:

```json
{
  "event_id": "10000000-0000-0000-0000-000000000001",
  "name": "Scalable Services Demo Night",
  "location": "Pune, India",
  "starts_at": "2026-11-14T18:00:00Z",
  "published": true,
  "ticket_types": [
    {"ticket_type_id":"20000000-0000-0000-0000-000000000001","name":"General Admission","price":500.00,"currency":"INR","published":true},
    {"ticket_type_id":"20000000-0000-0000-0000-000000000002","name":"VIP","price":1500.00,"currency":"INR","published":true}
  ]
}
```

Booking should check the requested `ticket_type_id` appears in this event's `ticket_types` before attempting inventory reservation. Unknown/unpublished event returns 404 with code `EVENT_NOT_FOUND`.

## Reserve tickets

`POST /api/v1/reservations`

Required headers: `Idempotency-Key: reserve-<booking_id>` and `X-Correlation-Id: <value>` (the latter may be omitted by a direct client; the service then generates one).
The Inventory service stores the idempotency key, a SHA-256 request fingerprint and the original response body.

Request:

```json
{
  "booking_id": "<booking UUID or unique ID, max 64 chars>",
  "event_id": "10000000-0000-0000-0000-000000000001",
  "ticket_type_id": "20000000-0000-0000-0000-000000000001",
  "quantity": 1
}
```

New reservation returns 201; replay with the same `Idempotency-Key` and same body returns 200 with `Idempotent-Replay: true` and the original stored response. Reusing the key with a different payload returns 409 `IDEMPOTENCY_KEY_REUSED`. Reusing a `booking_id` under a different key returns 409 `BOOKING_ALREADY_RESERVED`. Insufficient capacity returns 409 `INSUFFICIENT_INVENTORY`. The atomic conditional update ensures simultaneous requests cannot reduce available quantity below zero.

Success response fields: `reservation_id`, `booking_id`, `event_id`, `ticket_type_id`, `quantity`, `status`, `created_at`, `updated_at`. The initial status is `RESERVED`.

## Confirm or compensate

- After payment success: `POST /api/v1/reservations/{reservation_id}/confirm`
- After payment failure: `POST /api/v1/reservations/{reservation_id}/release`

Confirmed reservations remain deducted from availability. Release changes only `RESERVED` to `RELEASED` and restores capacity in the same DB transaction. Repeating release returns the existing `RELEASED` reservation and does not restore capacity again. Releasing a `CONFIRMED` reservation returns 409 `RESERVATION_ALREADY_CONFIRMED`.

## Shared conventions

- Pass/forward `X-Correlation-Id` through service calls; the services echo it in the response.
- Use `/api/v1/...`, snake_case JSON, and the common error envelope: `timestamp`, `status`, `error`, `code`, `message`, `path`, `correlation_id`, optional `details`.
- Database users are independent (`catalog_user` and `inventory_user`). Never query the other service's tables.
