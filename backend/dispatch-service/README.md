# Member 7 – Dispatch and Reports

Dispatching (assigning couriers to new parcels), courier workload, ongoing
deliveries and delivery reports. The API contract is
[`contracts/openapi/dispatch-service.yaml`](../../contracts/openapi/dispatch-service.yaml).

## What dispatch owns, and where the rest comes from

Dispatch owns two tables in `dispatch_db`:

| Table | Holds |
|---|---|
| `dispatch_assignments` | One row per assignment: parcel, courier, who assigned it, when, and state `ACTIVE` / `COMPLETED` / `CANCELLED`. At most one ACTIVE row per parcel (unique partial index). |
| `dispatch_report_events` | Every parcel status event received, once per `eventId`. Reports, ongoing deliveries and parcel timelines are built from it. |

Everything else comes from the owning service's API, with the caller's token:

| Data | Call |
|---|---|
| Waiting parcels | parcel-service `GET /api/parcels/dispatch?status=CREATED` |
| One parcel | parcel-service `GET /api/parcels/dispatch/{id}` |
| Record the courier on the parcel | parcel-service `PATCH /api/parcels/{id}/courier` `{"courierUserId": n}` |
| Couriers and availability | courier-service `GET /api/couriers/profiles?active=true` (all pages) |
| Status events | RabbitMQ queue `dispatch.parcel-status-changed` (exchange `swiftdrop.events`, key `parcel.status-changed`) |

Dispatch publishes `parcel.assigned` after each assignment or reassignment
([schema](../../contracts/events/parcel.assigned.v1.schema.json)).

## Courier ids

- `courierId` in every dispatch API is the courier-service **profile id**.
- Each assignment also stores the courier's identity **user id**. That is what
  parcel-service stores as the parcel's courier (it checks it when a courier
  updates a status), and what status events carry as `courierId`.
- Courier-service reads a courier's assignments from
  `GET /api/dispatch/couriers/{profileId}/assignments` with the courier's own
  token. A courier only sees their own rows. `version` increases only when
  dispatch cancels or reassigns an assignment, never when a delivery
  completes it, because courier-service rejects a retried submission when the
  version changed.

## Rules

- **Assign** (`POST /api/dispatch/assignments`): the parcel is CREATED and has no
  ACTIVE assignment, and the courier is active and AVAILABLE. Otherwise 404/400/409
  with a clear `error` message. The local row and the parcel-service update succeed
  or fail together.
- **Reassign** (`POST /api/dispatch/assignments/{parcelId}/reassign`) and
  **cancel** (`.../cancel`) work only while the parcel is CREATED.
  Parcel-service has no un-assign call yet (request R1 to Member 3), so after a
  cancel it keeps the old courier until the next assignment overwrites it.
- **Events**: DELIVERED or FAILED marks the ACTIVE assignment COMPLETED; CANCELLED
  marks it CANCELLED. A repeated `eventId` is ignored. An unreadable or invalid
  event is rejected without requeue, so it goes to the dead-letter exchange.
  Other failures are retried three times, then dead-lettered.
- **Roles**: DISPATCHER and ADMIN for everything, plus COURIER on the courier
  hand-off endpoint only.

## Configuration

| Variable | Default |
|---|---|
| `PARCEL_SERVICE_URL` | `http://localhost:8082` |
| `COURIER_SERVICE_URL` | `http://localhost:8084` |
| `PARCEL_CREATED_PATH`, `PARCEL_PATH`, `PARCEL_ASSIGN_COURIER_PATH`, `COURIER_PROFILES_PATH` | see `application.properties` |

## Run and test

```bash
infrastructure/scripts/run-service.sh dispatch-service   # from the repository root
cd backend/dispatch-service && ./mvnw test               # Windows: .\mvnw.cmd test
```

Tests do not need RabbitMQ. Tests that start the service use Testcontainers
PostgreSQL and are skipped when Docker is not running. For the full flow, parcel-service,
courier-service and identity-service must be running too.
