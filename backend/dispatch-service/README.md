# Dispatch and Reports

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

- `GET /api/dispatch/parcels/unassigned` and `GET /api/dispatch/parcels` return
  bounded, paginated results with filters and allow-listed sorting.
- `POST /api/dispatch/parcels/{id}/assignment` locks the Dispatch projection
  row, verifies the courier projection's `COURIER` role, and atomically records
  the assignment and previous courier. Assignment changes are only accepted
  while the projected status is `CREATED`.
- `GET /api/dispatch/parcels/{id}` includes the projection's ordered status
  timeline. The existing `reports.parcel-status-changed` RabbitMQ queue feeds
  status events into that projection.
- Reports aggregate in PostgreSQL for a required UTC date range (at most two
  years). Status counts represent parcels entering each status; volume counts
  completed deliveries by day/week. CSV uses the matching report endpoint plus
  `.csv`.

## Temporary ownership boundaries

Parcel and Courier currently have no read/assignment APIs, and Dispatch cannot
read their databases. The migration therefore creates minimal Dispatch-owned
demo projections; replacing those reads with the owners' APIs/events is marked
with TODOs. Assignment transactions update only `dispatch_db` and publish
`parcel.assigned` after commit to the existing `swiftdrop.events` exchange.
They do **not** update Parcel Core's canonical `assigned_courier_id`. The
assignment event is documented in
[`contracts/events/parcel.assigned.v1.schema.json`](../../contracts/events/parcel.assigned.v1.schema.json).

Parcel and courier IDs in the demo projection are UUIDs to match Parcel Core.
The Identity JWT subject is stored as an opaque string in audit records because
Identity currently issues numeric IDs. The existing status event has no
failure reason, delivery timestamp, structured area, or zone fields. Demo
failure reasons are seeded locally; live failures without a source reason are
reported as unavailable. Fees are grouped by pickup/drop-off area using the
parcel `charge`, not Pricing's zone/rule data. Replace these assumptions when
Parcel, Courier and Pricing expose owner-controlled contracts.
