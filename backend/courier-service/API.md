# Courier API: Dispatcher assignments and Courier activity

Dispatch Service owns assignment, reassignment and cancellation. Dispatcher selects
an active AVAILABLE Courier profile; Dispatch publishes `parcel.assigned`.
Courier consumes that message, saves a projection in `courier_db`, and displays it
only to the assigned courier. Courier saves delivery activity/evidence locally and
publishes its own RabbitMQ events. Merchant has no assignment authority here.

Send `Authorization: Bearer <Identity access_token>` on `/api/couriers/**`.
The gateway already routes these endpoints. Numeric JWT subjects are Identity user
IDs. Numeric profile IDs (`courier_profiles.id`) are different from Identity IDs.
The UUID `courierReference` is an internal/legacy correlation value.

## Directory and availability

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/couriers/profiles` | DISPATCHER, ADMIN | Paginated real Courier profiles, including numeric `id` and `userId` |
| GET | `/api/couriers/profiles/{id}` | DISPATCHER, ADMIN | One numeric profile |
| GET | `/api/couriers/available` | DISPATCHER, ADMIN | Active AVAILABLE profiles and active workload count |
| GET | `/api/couriers/{id}/availability` | DISPATCHER, ADMIN | One numeric profile's availability and workload |
| GET, PUT | `/api/couriers/me` | COURIER | Read/edit own profile |
| GET, PATCH | `/api/couriers/me/availability` | COURIER | Read/edit own availability |
| POST | `/api/couriers` | ADMIN | Provision an Identity-verified Courier profile |
| PATCH | `/api/couriers/{id}/active` | ADMIN | Activate/deactivate a Courier profile |

The incoming Dispatch `HttpDispatchDataClient` already calls
`GET /api/couriers/profiles?active=true&page=0&size=100` with the dispatcher's JWT.
This contract works without changes to Dispatch. Profile responses contain
`id`, `userId`, `name`, `phone`, `vehicleType`, `vehicleNumber`, `area`,
`availability`, `active`, `version`, `createdAt`, `updatedAt`.

`/profiles` filters: `availability`, `area`, `active` (default true), `page`
(default 0), `size` (default 20; max 100). `/available` supports `area`,
`vehicleType`, `page` and `size`. Vehicle types: BICYCLE, MOTORCYCLE,
THREE_WHEELER, CAR, VAN, TRUCK. Area matching is case-insensitive and exact.

```http
GET /api/couriers/available?area=Colombo&vehicleType=VAN&page=0&size=20
```

```json
{
  "content": [{
    "id": 10,
    "courierReference": "10000000-0000-4000-8000-000000000010",
    "name": "Courier Name", "vehicleType": "VAN", "area": "Colombo",
    "availability": "AVAILABLE", "active": true, "activeDeliveryCount": 2
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Set availability with `PATCH /api/couriers/me/availability`:

```json
{"availability":"AVAILABLE"}
```

Availability is AVAILABLE, BUSY or OFFLINE; it is separate from parcel activity.
A courier may carry multiple parcels. Identical availability updates do not
produce duplicate change events. Only ADMIN changes the activation flag.

The root UUID directory and `GET /api/couriers/{uuid}` are retained for legacy
callers, with DISPATCHER/ADMIN access. New integrations use numeric `/profiles`.

## Assignment handoff through RabbitMQ

Use the assignment APIs documented in Dispatch Service. Courier does not expose
`POST /api/couriers/assignments`, cancellation writes or `/merchant/deliveries`.
Those APIs belonged to the superseded Merchant workflow and have been removed.
No browser-provided owner ID can create a Courier assignment.

Courier subscribes to `swiftdrop.events` / `parcel.assigned`. The current producer
contract (`contracts/events/parcel.assigned.v1.schema.json`) is:

```json
{
  "eventId":"81000000-0000-4000-8000-000000000001",
  "eventType":"ParcelAssigned",
  "occurredAt":"2026-11-17T08:00:00Z",
  "parcelId":"82000000-0000-4000-8000-000000000001",
  "trackingNumber":"SD10480015",
  "courierId":"10", "courierUserId":"42",
  "previousCourierId":null, "previousCourierUserId":null,
  "assignedBy":"7"
}
```

Both numeric IDs must match the same Courier profile. Unknown/mismatched profiles
are rejected transactionally; provision/import the correct profile first, then
redrive the failed message from Courier's dead-letter queue. UUID-only assignment
events remain readable for older fixtures. New events must send numeric IDs.

Duplicate event IDs do not create a second assignment. Older assignments cannot
restore a previous courier. A newer assignment revokes the previous courier's
access and increments the local version. When a new assignment follows FAILED,
the local attempt increases and earlier proof records are preserved. Optional
explicit assignment/attempt/version fields remain supported for trusted legacy
fixtures; the current Dispatch producer does not supply them.

## Data available from the merged services

The new Dispatch event contains assignment identity/tracking, **not** recipient,
addresses, parcel dimensions, scheduled date or assignment cancellation. Existing
Parcel status events contain status/tracking and ownership metadata, but not the
full delivery details. Courier never invents those fields or queries another
service's database. Supply display data through the supported optional
`parcel.delivery-snapshot.v1` RabbitMQ handoff or an operator's verified import
into Courier's own tables. Missing optional values remain unavailable in the UI.

`demo/manual_merchant_example.sql` is a historical, Courier-only parcel import
example. Its merchant ID is source metadata; it no longer authorizes assignment.
Use a verified source parcel UUID/tracking number when importing a real parcel.
Do not use the older demo seed scripts against the new Dispatch APIs unchanged.

## Courier activity and evidence

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/couriers/me/deliveries` | Own Today, Active or History list, page and size |
| GET | `/api/couriers/me/deliveries/{parcelId}` | Own detail including projected history |
| GET | `/api/couriers/me/deliveries/{parcelId}/status` | Own current status, assignment/version, attempt and timestamp |
| GET | `/api/couriers/me/deliveries/{parcelId}/history` | Own canonical history |
| POST or PATCH | `/api/couriers/me/deliveries/{parcelId}/status` | Save a JSON status submission |
| POST | `/api/couriers/me/deliveries/{parcelId}/status` | Multipart JSON `data` plus optional `photo` |
| GET | `/api/couriers/me/delivery-submissions/{id}` | Own stable submission result |
| GET | `/api/couriers/me/deliveries/{parcelId}/tracking` | Own confirmed evidence checkpoints |
| GET | `/api/couriers/me/deliveries/{parcelId}/proofs` | Own confirmed attempt proofs |
| GET | `/api/couriers/me/deliveries/{parcelId}/proofs/{proofId}/photo` | Authorized private proof photo |

These endpoints require COURIER. Valid ordinary progression is:

```text
CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
                                                     -> FAILED
```

A status cannot be skipped or moved backwards. Dispatch cancellation is separate
from courier status submission. JSON uses `IN_TRANSIT` and `DELIVERED` (not the
display spellings "transit" or "dilivered").

```json
{
  "submissionId":"83000000-0000-4000-8000-000000000001",
  "status":"PICKED_UP",
  "locationDescription":"Collected from the pickup reception"
}
```

Use a new submission UUID for each distinct action. Retry the identical body
and photo with the **same** ID after a timeout. Do not change the payload of an
existing submission. DELIVERED needs a nonblank `recipientName` or a valid photo.
PICKED_UP, DELIVERED and FAILED need GPS coordinates or a location description.
FAILED needs `failureReason` (RECIPIENT_ABSENT, WRONG_ADDRESS, REFUSED,
INACCESSIBLE_LOCATION, DAMAGED_PARCEL, OTHER); OTHER also needs a note. Existing
5 MB JPEG/PNG, GPS-pair/range and evidence privacy checks still apply.

For Dispatch-assigned deliveries, HTTP 200/CONFIRMED means Courier committed its
own status, history, evidence confirmation and outgoing event atomically. It
does **not** mean that another service's parcel table was changed. A rejected
transition returns 409/REJECTED without changing the delivery status. Concurrent
reassignment/version mismatches are rejected, and duplicate submissions do not
duplicate history, proofs or outgoing events.

## Outgoing RabbitMQ events

Courier commits activity/history/evidence confirmation and the outbox entry in
one transaction. It publishes persistent JSON on `swiftdrop.events`:

| Routing key | Event type | Data |
| --- | --- | --- |
| `courier.delivery-status-changed.v1` | CourierDeliveryStatusChanged | Submission, parcel/tracking, numeric courier profile/user IDs, assignment/version, attempt, old/new status and timestamp; source merchant metadata when known |
| `courier.availability-changed.v1` | CourierAvailabilityChanged | Courier profile/user IDs, availability, active and profile version |

Envelope: UUID `eventId`, `eventType`, `schemaVersion:1`, UTC `occurredAt`.
No credentials, precise GPS or private photo paths are published. Durable
subscribers deduplicate event IDs. Publisher confirms and mandatory routing keep
failed/unrouted events pending for retry; delivery is at least once.

**Integration limits in develop:** Dispatch consumes `parcel.status-changed`,
not Courier's status event, and does not publish cancellation events. Therefore
Courier status changes do not automatically update Parcel/Dispatch databases,
and Dispatch-only cancellations cannot immediately revoke Courier projections.
The responsible service owners need a supported consumer/command contract and
cancellation handoff. This change does not impersonate Parcel's event producer,
add direct database writes, or modify those services. The opt-in legacy `http`
mode is not the default RabbitMQ workflow.

## Storage, migrations and errors

Courier reads/writes only `courier_db`. Existing Flyway V1-V5 are preserved,
including the previously applied V5 migration; unused Merchant receipt columns
and tables are retained for upgrade compatibility, with no public write API.
No existing DB records are reassigned by this code change.

401: missing/invalid token. 403: wrong role, inactive Courier, or another
courier's parcel. 404: missing numeric profile or projection. 400: invalid
input/pagination/evidence. 409: skipped status, reused submission payload or
changed assignment. Broker failure leaves the outbox pending; a local confirmed
submission still means Courier committed its own data.

Run `./mvnw test` in this service for disposable PostgreSQL/RabbitMQ integration
coverage. See [MERGE_NOTES.md](MERGE_NOTES.md) for merge scope and team handoff.
