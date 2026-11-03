# event-catalog

**Owner:** Shreya. Publishes event and ticket-type metadata; it does not own available ticket counts.

## Run
From the repository root, run `docker compose up -d`. Copy `.env.example` to `.env` if you need overrides, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

The service runs on port `8081`, uses `catalog_db` / `catalog_user`, and exposes Swagger at `http://localhost:8081/swagger-ui.html`.

## Endpoints
- `GET /health`
- `GET /api/v1/events`
- `GET /api/v1/events/{event_id}`

An event is bookable only if it is published. The event response contains published ticket types with stable IDs, price and currency. Ticket availability belongs exclusively to Inventory.
