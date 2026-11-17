# Courier API: Merchant assignment and Courier activity

Courier owns this workflow and all of its tables in `courier_db`. Merchant calls
these APIs with its existing Identity-issued JWT. No Merchant code, Merchant
database, or other service database is read or changed by these APIs.

All paths already fit the gateway's `/api/couriers/**` route. Send
`Authorization: Bearer <access_token>` and `Content-Type: application/json`.
The caller's numeric Identity user ID is the JWT subject; never send an actor ID
or merchant owner ID in an API request to impersonate another account.

## Roles and identifiers

- **MERCHANT** lists available couriers, assigns/reassigns/cancels its own
  deliveries, and reads its own deliveries' activity/history.
- **COURIER** reads its own assignments, edits its own availability, and records
  delivery progress and proof. It cannot edit another courier's deliveries.
- **ADMIN** provisions/deactivates courier profiles and can read the directory.
- DISPATCHER is not an assignment authority in this workflow.

`courierId` in assignment requests is the numeric `courier_profiles.id`, returned
as `id` by the available-courier API. It is not the Identity user ID and not the
legacy UUID. `courierReference` is the stable UUID used for event correlation.
`parcelId`, `assignmentId` and `submissionId` are UUIDs.

## Directory and availability

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/couriers/available` | MERCHANT, ADMIN | Active AVAILABLE profiles from `courier_profiles`, with active workload count |
| GET | `/api/couriers/{id}/availability` | MERCHANT, ADMIN | Current availability/activation/workload for one numeric profile ID |
| GET | `/api/couriers/profiles` | MERCHANT, ADMIN | Paginated profile directory; filter availability, area and active |
| GET | `/api/couriers/me` | COURIER | Own profile |
| PUT | `/api/couriers/me` | COURIER | Own contact/vehicle/area fields |
| GET | `/api/couriers/me/availability` | COURIER | Own availability and workload |
| PATCH | `/api/couriers/me/availability` | COURIER | Set AVAILABLE, BUSY or OFFLINE |
| POST | `/api/couriers` | ADMIN | Provision a verified COURIER Identity user |
| PATCH | `/api/couriers/{id}/active` | ADMIN | Activate/deactivate the operational profile |

Available query parameters: optional `area` (case-insensitive exact match), optional
`vehicleType` (BICYCLE, MOTORCYCLE, THREE_WHEELER, CAR, VAN, TRUCK), `page` (default
0), `size` (default 20, maximum 100). Only active AVAILABLE profiles are returned.

```http
GET /api/couriers/available?area=Colombo&vehicleType=VAN&page=0&size=20
```

```json
{
  "content": [{
    "id": 10,
    "courierReference": "10000000-0000-4000-8000-000000000010",
    "name": "Courier Name",
    "vehicleType": "VAN",
    "area": "Colombo",
    "availability": "AVAILABLE",
    "active": true,
    "activeDeliveryCount": 2
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

The existing root UUID directory remains for compatibility; new Merchant
integration should use `/available` and its numeric `id`. Availability is separate
from delivery progress: a courier can carry multiple parcels while AVAILABLE.
The service does not invent a capacity limit or automatically mark every courier
BUSY after one assignment. An unchanged availability PATCH is idempotent and
does not create another change event.

```json
{"availability":"AVAILABLE"}
```

## Merchant assignments

Before assignment, the parcel must exist in `courier_delivery_parcels` with its
verified `merchant_user_id`. That value must equal the caller's JWT subject.
The API never claims an unknown parcel for whoever asks first.

Ownership can arrive through a trusted `parcel.delivery-snapshot.v1` message's
explicit `merchantUserId`, or the existing Parcel status event's numeric
`merchantId` (Parcel's creation contract uses the Identity user ID). A conflicting
incoming owner is rejected. Alternatively, the responsible developer/operator
can import verified data with SQL in **Courier's own database**. See
[`demo/manual_merchant_example.sql`](demo/manual_merchant_example.sql).

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/couriers/assignments` | Assign, reassign before pickup, or start a new attempt after FAILED |
| POST | `/api/couriers/assignments/{parcelId}/cancel` | Cancel a locally managed assignment before pickup |
| GET | `/api/couriers/merchant/deliveries` | Own assigned deliveries; optional exact `status`, page, size |
| GET | `/api/couriers/merchant/deliveries/{parcelId}` | Own delivery and parcel details |
| GET | `/api/couriers/merchant/deliveries/{parcelId}/status` | Own current activity |
| GET | `/api/couriers/merchant/deliveries/{parcelId}/history` | Own canonical Courier history |

All these endpoints require MERCHANT. There is no owner ID filter that can be
used to read another merchant's deliveries.

```http
POST /api/couriers/assignments
```
```json
{
  "submissionId":"81000000-0000-4000-8000-000000000001",
  "parcelId":"82000000-0000-4000-8000-000000000001",
  "courierId":10,
  "deliveryDate":"2026-11-16"
}
```

`deliveryDate` is optional; provided dates are planned schedule data. Assignment
returns HTTP 200 with the real local assignment ID, numeric courier ID, UUID
reference, owner, attempt, version, active flag, date and assigned time. Repeating
the same submission ID and identical request returns the same operation receipt,
even if the delivery later progressed. Read `/status` for current state. Changed
content with the same ID returns 409; another merchant's ID returns 403.

The selected profile must be active and AVAILABLE. Before pickup, reassignment
revokes the former courier's delivery access and increments assignment version.
After FAILED, a new Merchant assignment increments the attempt and resets the
Courier delivery to CREATED while retaining prior proof/history. DELIVERED and
CANCELLED deliveries cannot be reassigned. Submission/parcel locks prevent
duplicate assignments during concurrent identical requests.

```http
POST /api/couriers/assignments/82000000-0000-4000-8000-000000000001/cancel
```
```json
{"submissionId":"81000000-0000-4000-8000-000000000002","note":"Cancelled before pickup"}
```

An imported legacy assignment must first be explicitly adopted through the
Merchant assignment API with verified ownership. The new APIs do not silently
take authority over an unrelated external assignment.

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

A status cannot be skipped or moved backwards. Merchant cancellation is separate
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

For Merchant-created assignments, HTTP 200/CONFIRMED means Courier committed its
own status, history, evidence confirmation and outgoing event atomically. It
does **not** mean that another service's parcel table was changed. A rejected
transition returns 409/REJECTED without changing the delivery status. Concurrent
reassignment/version mismatches are rejected, and duplicate submissions do not
duplicate history, proofs or outgoing events.

## RabbitMQ events

Courier saves each business change and an outbox entry in the same database
transaction. A scheduled publisher sends persistent JSON to `swiftdrop.events`:

| Routing key | Event type | Main data |
| --- | --- | --- |
| `courier.delivery-assigned.v1` | CourierDeliveryAssigned | Owner, parcel/tracking, assignment/version, numeric courier ID, UUID reference, attempt, schedule, prior courier |
| `courier.delivery-assignment-cancelled.v1` | CourierDeliveryAssignmentCancelled | Owner/assignment identifiers, inactive state, cancellation note |
| `courier.delivery-status-changed.v1` | CourierDeliveryStatusChanged | Owner, courier user/profile, parcel/tracking, assignment/version, attempt, submission ID, old/new status and changed time |
| `courier.availability-changed.v1` | CourierAvailabilityChanged | Courier user/profile, availability, activation and profile version |

The envelope has UUID `eventId`, `eventType`, `schemaVersion: 1` and UTC
`occurredAt`. Tokens, passwords, private photo paths and precise GPS are not
published. Consumers must create their own durable queue/bindings and deduplicate
by event ID. If a consumer has not bound a queue yet, mandatory returns keep the
event pending; it is **not** falsely marked delivered. Failed/unrouted sends retry
with backoff. Broker confirms provide at-least-once publication, not exactly-once
delivery.

Once an assignment is locally managed, delayed legacy assignment/status messages
cannot override its owner or current activity. Trusted parcel snapshots can still
refresh parcel display fields but cannot replace merchant ownership or add
conflicting external activity history to a locally managed delivery.

Other teams may consume these Courier-owned events to update their databases;
this work does not add consumers or change code in Merchant, Parcel or Dispatch.

## Database preparation and errors

Run Courier normally to apply Flyway V5; do not manually change Flyway history.
You can inspect the directory using:

```sql
SELECT id, user_id, name, area, vehicle_type, availability, active,
       dispatch_courier_id AS courier_reference
FROM courier_profiles
ORDER BY id;

SELECT parcel_id, tracking_number, merchant_user_id, status
FROM courier_delivery_parcels
ORDER BY tracking_number;
```

The historical UUID column name is retained for data compatibility; it does not
give a DISPATCHER role authority in the new workflow. Never infer merchant
ownership from a name/email, courier ID, or an untrusted assignment request.

| HTTP | Meaning |
| --- | --- |
| 400 | Invalid JSON, enum, pagination, coordinates or required field |
| 401 | Missing/invalid/expired authentication |
| 403 | Wrong role, wrong merchant/courier owner, or inactive courier |
| 404 | Missing profile, imported parcel or assignment |
| 409 | Unavailable courier, invalid transition, missing verified ownership, changed/reused submission, or concurrent conflict |
| 413 | Photo above the existing upload limit |

All times are persisted as UTC instants; the existing Courier UI displays
Asia/Colombo time. Availability and parcel activity are different fields.
