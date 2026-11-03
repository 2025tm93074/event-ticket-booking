# inventory

**Owner:** Shreya. Owns ticket capacity and reservations. All writes to capacity are atomic database updates.

## Run
From repository root, run `docker compose up -d`. Copy `.env.example` to `.env` if needed, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

The service runs on port `8082`, uses `inventory_db` / `inventory_user`, and exposes Swagger at `http://localhost:8082/swagger-ui.html`.

## Endpoints
- `GET /health`
- `GET /api/v1/inventory/{ticket_type_id}`
- `POST /api/v1/reservations`
- `GET /api/v1/reservations/{reservation_id}`
- `POST /api/v1/reservations/{reservation_id}/confirm`
- `POST /api/v1/reservations/{reservation_id}/release`

`POST /api/v1/reservations` requires `Idempotency-Key`. The service stores the key, a request fingerprint, and the original response; same key/same payload replays the stored response and same key/different payload is rejected. `booking_id` is also unique. `RESERVED -> CONFIRMED` and `RESERVED -> RELEASED` are the only state transitions. Releasing is safe to repeat and confirmed inventory cannot be released.
