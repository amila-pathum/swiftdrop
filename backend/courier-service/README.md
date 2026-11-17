# Courier Service

Courier profiles, availability, Merchant-authorized assignments, courier delivery
activity, history and proof. Java 21 / Spring Boot, port 8084, owning `courier_db`.

## Current workflow

Merchant selects an active AVAILABLE courier through Courier APIs. Courier checks
the parcel's verified merchant owner, saves its own assignment/activity state, and
publishes Courier-owned RabbitMQ events through a transactional outbox. No other
service database is queried or modified. No Merchant code changes are included.
DISPATCHER is not an assignment authority in this workflow.

See [API.md](API.md) for every endpoint, request examples, roles, identifier types,
status transitions, errors and RabbitMQ event contracts. For your manual database
preparation, see [demo/manual_merchant_example.sql](demo/manual_merchant_example.sql).

## Main endpoints

| Method | Path | Role |
| --- | --- | --- |
| GET | `/api/couriers/available` | MERCHANT, ADMIN |
| GET | `/api/couriers/{id}/availability` | MERCHANT, ADMIN |
| POST | `/api/couriers/assignments` | MERCHANT |
| POST | `/api/couriers/assignments/{parcelId}/cancel` | MERCHANT owner |
| GET | `/api/couriers/merchant/deliveries` | MERCHANT owner |
| GET | `/api/couriers/merchant/deliveries/{parcelId}` | MERCHANT owner |
| GET | `/api/couriers/merchant/deliveries/{parcelId}/status` | MERCHANT owner |
| GET | `/api/couriers/merchant/deliveries/{parcelId}/history` | MERCHANT owner |
| GET, PATCH | `/api/couriers/me/availability` | COURIER |
| GET | `/api/couriers/me/deliveries` | COURIER owner |
| GET | `/api/couriers/me/deliveries/{parcelId}` | COURIER owner |
| GET, POST, PATCH | `/api/couriers/me/deliveries/{parcelId}/status` | COURIER owner |
| GET | `/api/couriers/me/deliveries/{parcelId}/history` | COURIER owner |

The existing ADMIN provisioning/activation, own profile editing, submission polling,
private tracking/proof/photo endpoints remain available; see the full reference.

## State and data preparation

Availability (AVAILABLE/BUSY/OFFLINE) is separate from activity:
`CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED/FAILED`.
Merchants can cancel before pickup or authorize a new attempt after FAILED.
Current owner/assignment/version checks, strict transitions and stable submission
IDs prevent cross-owner updates and duplicate evidence/events.

The parcel row must be imported into Courier with its verified `merchant_user_id`
(the Identity JWT subject, not a Merchant business-profile ID). Trusted existing
Parcel events can supply that ID, or the responsible developer can import verified
data using Courier-only SQL. The browser cannot claim ownership by sending an ID.
Unverified/missing ownership is rejected rather than guessed.

Flyway V5 adds ownership, locally managed assignment flags, Merchant request
receipts and an outbox. Old imported assignments remain read-only until explicitly
adopted through the Merchant assignment API. Once locally managed, legacy external
assignment/status events cannot overwrite local delivery activity or ownership.

## RabbitMQ

Default directory/delivery reads use Courier's own database. Inbound parcel
snapshots/status messages can populate its data. Confirmed local changes create
outbox messages on `swiftdrop.events`:

- `courier.delivery-assigned.v1`
- `courier.delivery-assignment-cancelled.v1`
- `courier.delivery-status-changed.v1`
- `courier.availability-changed.v1`

Publisher confirms, mandatory routing and retries keep failed/unrouted messages
pending. Downstream services bind their own durable queues and deduplicate event
IDs. No credentials, precise GPS or private photo paths are published.

HTTP 200/CONFIRMED for a local delivery update means Courier committed its state,
history, evidence confirmation and outbox entry. It does not claim another
service's database was updated; other owners may consume these Courier events.

## Running and checks

Use the existing root environment/run-service tooling with `DB_PROFILE=local` or
`DB_PROFILE=rds`; keep credentials out of source. Flyway owns migrations, Hibernate
validates the schema, and the gateway already forwards `/api/couriers/**`.
Keep the default delivery source (`rabbit`, backed by local tables) for the new
workflow; the old opt-in `http` adapters are historical integration code.

```bash
DB_PROFILE=rds bash infrastructure/scripts/run-service.sh courier-service
# From backend/courier-service:
./mvnw test
```

Tests use disposable PostgreSQL/RabbitMQ containers and include role/ownership,
concurrent duplicate submissions, reassignment/cancellation, failed reattempts,
proof preservation, full status progression and unrouted-event retry. Existing
RDS/demo records and credentials are not required for those tests.

The Courier detail UI displays recipient/pickup/drop-off data, optional contents
and dimensions, timeline/history and the existing evidence form. Missing optional
source fields remain explicitly unavailable. Earlier Dispatcher-oriented fixture
scripts in [demo/README.md](demo/README.md) are historical and are not the current
Merchant assignment contract.
