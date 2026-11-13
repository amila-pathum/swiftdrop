# Member 7 – Dispatch and Reports

The Dispatch Service verifies Identity JWTs itself. Every endpoint under
`/api/dispatch` requires `DISPATCHER` or `ADMIN`. See
[`contracts/openapi/dispatch-service.yaml`](../../contracts/openapi/dispatch-service.yaml)
for the request/response contract.

## Local run and tests

From the repository root, start PostgreSQL and RabbitMQ using the existing
`.env` and Compose setup. Use the existing environment-loading script from
Git Bash/WSL to run the service:

```bash
infrastructure/scripts/run-service.sh dispatch-service
```

Run tests from PowerShell:

```powershell
Set-Location backend\dispatch-service
.\mvnw.cmd test
```

The migration seeds three courier profiles and parcels in CREATED, active,
DELIVERED and FAILED states. It is intended for local assignment/report demos.
The database test suite uses Testcontainers and skips container-backed tests
when Docker is unavailable.

## Behavior

- `GET /api/dispatch/parcels/unassigned` returns CREATED Parcel Service results
  excluding parcels with an ACTIVE Dispatch assignment.
- `GET /api/dispatch/couriers` reads owner-service profiles and adds active
  assignment counts from `dispatch_assignments`.
- `POST /api/dispatch/assignments` requires a CREATED parcel and an active,
  AVAILABLE courier. Reassignment closes the prior assignment as CANCELLED;
  `POST /api/dispatch/assignments/{parcelId}/cancel` cancels before pickup.
  Assignment rows record parcel, courier, dispatcher, timestamp and lifecycle
  state (`ACTIVE`, `COMPLETED`, `CANCELLED`).
- `GET /api/dispatch/deliveries` supports status and tracking-number filters.
- `GET /api/dispatch/couriers/{courierId}/parcels` returns active assignments
  for the courier service to consume.
- `GET /api/dispatch/parcels/{id}` includes the projection's ordered status
  timeline. The dedicated `dispatch.parcel-status-changed` RabbitMQ queue
  stores each unique event in `dispatch_report_events` and completes an active
  assignment on DELIVERED or FAILED.
- `GET /api/dispatch/reports/summary` computes daily outcomes, success rate,
  status counts and active courier deliveries exclusively from stored events.
  Dates are UTC and the range is limited to two years.

## Parcel and courier data sources

`DISPATCH_DATA_SOURCE=http` selects the HTTP client for the owner APIs:
`GET /api/parcels?status=CREATED`, `GET /api/parcels/{parcelId}`,
`GET /api/couriers`, and `GET /api/couriers/{courierId}`. Dispatch forwards the
authenticated JWT to those APIs. The explicit `projection` mode
(`DISPATCH_DATA_SOURCE=projection`, the local default) uses the existing
Dispatch-owned demo projection. HTTP failures are surfaced; they never silently
switch to demo data.

## Ownership boundaries

Assignment transactions update only `dispatch_db` and publish `parcel.assigned`
after commit to the existing `swiftdrop.events` exchange. They do **not**
update Parcel Core's canonical `assigned_courier_id`. The assignment event is documented in
[`contracts/events/parcel.assigned.v1.schema.json`](../../contracts/events/parcel.assigned.v1.schema.json).

Parcel and courier IDs in the demo projection are UUIDs to match Parcel Core.
The Identity JWT subject is stored as an opaque string in audit records because
Identity currently issues numeric IDs. The status event has no failure reason, structured area, or zone fields. Legacy
projection-based reports retain those limitations; the summary endpoint does
not depend on parcel projection rows.
