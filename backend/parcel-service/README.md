# SwiftDrop Parcel Service

Parcel Service owns parcel creation, private parcel reads, lifecycle history,
customer cancellation and receipts, courier assignment, public tracking, and
reliable publication of parcel-status events.

- Local URL: `http://localhost:8082`
- Gateway URL: `http://localhost:8080/api/parcels`
- Health: `GET http://localhost:8082/actuator/health`
- OpenAPI: [`../../contracts/openapi/parcel-service.yaml`](../../contracts/openapi/parcel-service.yaml)

## Run locally

From the repository root, start all services, PostgreSQL, RabbitMQ, and frontend:

```bash
bash infrastructure/scripts/run-all.sh
```

To start only Parcel Service after its dependencies are available:

```bash
bash infrastructure/scripts/run-service.sh parcel-service
```

Local PostgreSQL uses `parcel_db` on port `5433`; RabbitMQ uses port `5672`.
Credentials come from the root `.env` and must not be committed.

For the shared development RDS profile:

```bash
DB_PROFILE=rds bash infrastructure/scripts/run-service.sh parcel-service
```

RDS values come from `.env.rds` and the connection uses TLS. Never repair or
reset a shared database to resolve a local schema problem.

## Authentication and authorization

All endpoints except health and public tracking require an Identity Service JWT:

```http
Authorization: Bearer <access-token>
```

Parcel Service validates the JWT itself. Its subject must be a canonical positive
numeric Identity user ID, such as `42`; its `role` claim supplies authorization.

| Capability | Allowed roles | Additional rule |
|---|---|---|
| Public tracking | Anonymous | Privacy-safe response only |
| Customer creation/list/cancel/receipt | CUSTOMER | Parcel must belong to JWT subject where applicable |
| Merchant creation/list | MERCHANT | Listing returns merchant-owned parcels only |
| Private details/history | CUSTOMER, MERCHANT, DISPATCHER, ADMIN | Customers and merchants see only their own parcels |
| Status update | COURIER, DISPATCHER, ADMIN | Courier subject must equal assigned numeric courier ID |
| Courier assignment | DISPATCHER, ADMIN | Closed parcels cannot be assigned |
| Dispatch projection/status listing | DISPATCHER, ADMIN | Staff-only integration endpoints |

Private detail/history requests for another customer or merchant return `404`
instead of revealing whether the parcel exists.

## API overview

The OpenAPI file is the authoritative field-level contract.

| Method and path | Purpose |
|---|---|
| `GET /api/parcels/track/{trackingNumber}` | Public privacy-safe tracking |
| `POST /api/parcels` | Customer parcel creation |
| `POST /api/parcels/merchant` | Merchant parcel creation |
| `GET /api/parcels/my` | Paginated customer-owned parcels |
| `GET /api/parcels/merchant` | Paginated merchant-owned parcels |
| `GET /api/parcels/{id}` | Authorized private details |
| `GET /api/parcels/{id}/history` | Authorized chronological history |
| `PATCH` or `POST /api/parcels/{id}/status` | Lifecycle update |
| `PATCH /api/parcels/{id}/cancel` | Customer cancellation while `CREATED` |
| `GET /api/parcels/{id}/receipt` | PDF receipt for an owned delivered parcel |
| `PATCH /api/parcels/{id}/courier` | Assign numeric courier user ID |
| `GET /api/parcels?status=...` | Paginated staff status listing |
| `GET /api/parcels/dispatch` | Dispatch projection list |
| `GET /api/parcels/dispatch/{id}` | One Dispatch projection |

Paginated endpoints accept zero-based `page` and `size` from 1 through 100.
Customer and merchant lists are newest first; staff status lists are oldest first.

### Create a parcel

Customer creation requires `CUSTOMER`; merchant creation uses the same body at
`/api/parcels/merchant` and requires `MERCHANT`.

```json
{
  "receiverName": "Nimal Perera",
  "receiverPhone": "0771234567",
  "pickupAddressLine1": "10 Main Street",
  "pickupAddressLine2": "Floor 2",
  "pickupCity": "Colombo",
  "deliveryAddressLine1": "20 Lake Road",
  "deliveryAddressLine2": null,
  "deliveryCity": "Kandy",
  "weight": 2.500,
  "size": "MEDIUM",
  "parcelType": "STANDARD"
}
```

Sizes are `SMALL`, `MEDIUM`, and `LARGE`; parcel types are `STANDARD` and
`EXPRESS`. Cities must be one of Sri Lanka's 25 districts in the OpenAPI schema.
The caller cannot set sender ID, merchant ID, charge, tracking number, or status.

Success returns `201 Created`:

```json
{
  "id": "d488bd02-1a21-442a-bff4-f1d596d887b6",
  "trackingNumber": "SD-2026-000123",
  "status": "CREATED",
  "charge": 487.50,
  "createdAt": "2026-11-14T08:30:00Z"
}
```

The parcel, initial `null -> CREATED` history, and outbox event commit together.

### Pricing dependency

Creation calls `POST /api/pricing/quote` with pickup city, delivery city, weight,
and service type. The caller's JWT is forwarded in memory so Pricing validates it
independently; the token is never stored in parcel data, logs, or events.

- `PRICING_SERVICE_URL` defaults to `http://localhost:8086`.
- `PRICING_QUOTE_PATH` defaults to `/api/pricing/quote`.
- Unavailable Pricing returns `503`.
- Invalid pricing output returns `502`.
- Parcel Service never creates a fallback charge.

### Status transitions and idempotency

Statuses are `CREATED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`,
`DELIVERED`, `FAILED`, and `CANCELLED`. Illegal transitions are rejected.
Customer cancellation uses the dedicated cancellation endpoint.

Retrying courier clients should use `POST /api/parcels/{id}/status` with a UUID:

```http
Idempotency-Key: 5a82b09a-39e4-4510-b1ab-45c7b193719d
```

```json
{
  "status": "PICKED_UP",
  "note": "Collected from sender",
  "submissionId": "5a82b09a-39e4-4510-b1ab-45c7b193719d"
}
```

When `submissionId` is supplied, the header is required and both UUIDs must
match. An identical retry returns the stored response without another transition,
history row, or event. Reusing the key with different input returns `409`.

Courier updates compare the numeric JWT subject with `assigned_courier_id` while
holding the parcel write lock. Dispatch still has a legacy UUID projection; new
integrations must use `courierUserId` for the numeric Identity contract.

## Reliable status events

Creation and every successful transition insert `ParcelStatusChanged` into
`parcel_event_outbox` in the same transaction as parcel and history changes. A
scheduled publisher sends pending records to durable topic exchange
`swiftdrop.events` with routing key `parcel.status-changed`; failed sends remain
pending and retry.

Events contain identifiers and status metadata only, never phone numbers or
addresses.

- `swiftdrop.events.publisher-enabled=false` disables the publisher.
- `swiftdrop.events.publish-delay-ms` changes polling delay (default 1000 ms).

## Errors

Errors use one safe JSON field:

```json
{"error": "Parcel not found"}
```

Common statuses are `400` validation/input, `401` authentication, `403` role or
assignment, `404` missing/hidden parcel, `409` state/idempotency conflict,
`502` invalid pricing response, and `503` pricing unavailable.

## Database migrations

Flyway owns the schema and Hibernate only validates it. Current migrations are:

- V1 parcel and status-history tables
- V2/V3 numeric Identity user references
- V4 transactional event outbox
- V5 status-update idempotency

Never edit an applied versioned migration. Add the next migration version. Resolve
a checksum mismatch in a disposable local database by recreating only that local
service schema, never by repairing shared RDS history.

## Tests

From `backend/parcel-service`:

```powershell
.\mvnw.cmd test
```

Integration tests use disposable Testcontainers PostgreSQL and require Docker.
They do not read `.env.rds` or modify team databases. Coverage includes creation,
JWT roles, ownership, public privacy, pricing failures, transitions, concurrency,
idempotent retries, cancellation, receipts, outbox publishing, dispatch projections,
and migration startup.

For live Gateway verification, start all services, configure active Pricing zones
and rules, obtain a real JWT, and import
`verification/parcel-creation.postman_collection.json`. Each successful run creates
a real parcel.
