# Courier Service

Courier profiles, availability, assigned-delivery display, activity, history and
proof. Java 21 / Spring Boot, port 8084, owning `courier_db`.

Dispatcher assigns through Dispatch Service. Courier accepts the merged numeric
profile/user assignment contract over RabbitMQ, saves its own projection and
shows it only to the assigned courier. Courier saves activity and publishes
Courier-owned status/availability events through a transactional outbox.
Merchant assignment APIs have been removed.

See [API.md](API.md) for endpoints, examples, roles, evidence requirements,
RabbitMQ contracts and the current cross-service integration limits. See
[MERGE_NOTES.md](MERGE_NOTES.md) for the scope of the develop merge.

## Main endpoints

| Method | Path | Role |
| --- | --- | --- |
| GET | `/api/couriers/profiles` | DISPATCHER, ADMIN |
| GET | `/api/couriers/profiles/{id}` | DISPATCHER, ADMIN |
| GET | `/api/couriers/available` | DISPATCHER, ADMIN |
| GET | `/api/couriers/{id}/availability` | DISPATCHER, ADMIN |
| GET, PATCH | `/api/couriers/me/availability` | COURIER |
| GET | `/api/couriers/me/deliveries` | Assigned COURIER |
| GET | `/api/couriers/me/deliveries/{parcelId}` | Assigned COURIER |
| GET, POST, PATCH | `/api/couriers/me/deliveries/{parcelId}/status` | Assigned COURIER |
| GET | `/api/couriers/me/deliveries/{parcelId}/history` | Assigned COURIER |

ADMIN provisioning/activation, own profile editing, submission polling and private
tracking/proof/photo APIs remain available. Availability is AVAILABLE/BUSY/OFFLINE.
Delivery progression is CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY ->
DELIVERED/FAILED. Assignment/version checks prevent cross-courier updates.

Default `swiftdrop.deliveries.source=rabbit` uses Courier's own tables. Addresses,
contacts and optional parcel fields require a trusted snapshot/import; the
current Dispatch assignment event does not contain them. No other service's
DB is queried by this flow. Unrouted outbox events remain pending for retry.

## Running and checks

Use existing environment tooling; keep credentials out of source:

```bash
DB_PROFILE=rds bash infrastructure/scripts/run-service.sh courier-service
# From backend/courier-service:
./mvnw test
```

Tests use disposable PostgreSQL/RabbitMQ, including numeric assignment identity,
reassignment, status progression/idempotency, proof preservation, role restrictions
and broker retry. Existing migrations stay unchanged for deployed DB compatibility.
Old scripts in [demo/README.md](demo/README.md) target earlier service contracts;
read the compatibility warning before using them.
