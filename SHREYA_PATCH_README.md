# Shreya's branch-compatible service additions

This ZIP is intended to be extracted into the **root** of `event-ticket-booking`, on branch `feature/shreya2`.
It adds `services/event-catalog`, `services/inventory`, OpenAPI specs, scripts and a service-to-service contract document. It does not overwrite Mudit's existing Payment Service, root `docker-compose.yml`, or database initialization script.

## Apply on Windows

1. Extract the ZIP into the repository root, preserving folders.
2. Open PowerShell at the repository root and run `Set-ExecutionPolicy -Scope Process Bypass` if PowerShell blocks local scripts.
3. Run `./apply_shreya_changes.ps1` to copy the Maven Wrapper used by Mudit into both new service folders.
4. Ensure Docker Desktop is running, then run `docker compose up -d`.
5. In separate VS Code terminals, run `cd services/event-catalog; .\mvnw.cmd spring-boot:run` and `cd services/inventory; .\mvnw.cmd spring-boot:run`.
6. Check `http://localhost:8081/health`, `http://localhost:8082/health`, and each `/swagger-ui.html`.

Java 21 is required. Existing `infra/postgres/01-init-databases.sql` already creates `catalog_db` and `inventory_db` with separate users, so no edits to that file are needed.

## Tests and evidence

From each service folder, run `./mvnw.cmd test`. The Catalog tests cover published-event validation; Inventory unit tests cover capacity rejection and safe state transitions.

The live concurrency script expects the seeded General Admission capacity to be exactly five. Because test reservations persist, reset the local database before each fresh challenge demo with `docker compose down -v` then `docker compose up -d` (this deletes all local assignment databases/data). Start both services again, wait for `/health`, then run from repo root:

```powershell
python scripts/test_inventory_concurrency.py
python scripts/test_inventory_release.py
```

Run these scripts against local development data only. For a recorded concurrency result, run the concurrency script on a fresh database because it intentionally consumes all five General Admission tickets.

Integration details and stable demo IDs are in `docs/shreya-integration-contract.md`. Booking Service should validate the ticket type with Catalog, reserve via Inventory using `Idempotency-Key: reserve-<booking_id>`, call Payment, then call Inventory `confirm` on payment success or `release` on payment failure. All cross-service communication must use HTTP APIs, not database connections.
