# Courier service

Java 21 / Spring Boot backend on port 8084, owning `courier_db`. Requests go through the existing gateway prefix `/api/couriers/**`. All endpoints require JWT authentication and the documented role. No other service's database is accessed.

## Implemented scope

- Courier profiles, availability, admin activation, and the dispatcher's paginated courier list.
- Assigned-delivery aggregation using Dispatch and Parcel clients.
- Collection/progress/delivery/failure checkpoints with an optional GPS coordinate pair, accuracy, device capture time, location description and server receipt time.
- Per-attempt proof of delivery, failed-delivery reasons, and collection/destination address snapshots. Parcel and history references are UUIDs; courier and user IDs are numeric.
- Private JPEG/PNG storage, actual image validation, 5 MB upload limit, generated filenames, and authenticated photo downloads.
- Stable submission IDs, durable pending/unknown confirmation state, and retry protection. Confirmed checkpoints alone appear in tracking and proof responses.

This is checkpoint tracking, not continuous live GPS. Address snapshots describe the planned collection/destination addresses retrieved from Parcel. Actual collection location is the `PICKED_UP` checkpoint's GPS/description; actual delivery/failure location is the terminal checkpoint returned with its proof. A device location is evidence reported by the client, not independently verified GPS.

## Profile APIs

| Method and path | Role | Behavior |
| --- | --- | --- |
| `POST /api/couriers` | ADMIN | Provision a profile for a verified COURIER identity account; 201 created, 200 for an existing userId |
| `GET /api/couriers/me` | COURIER | Own profile; userId comes from JWT subject |
| `PUT /api/couriers/me` | COURIER | Replace editable profile/contact/vehicle fields |
| `PATCH /api/couriers/me/availability` | COURIER | Set AVAILABLE, BUSY or OFFLINE |
| `GET /api/couriers` | ADMIN, DISPATCHER | Filter by availability, area, active; paginate with page and size |
| `PATCH /api/couriers/{id}/active` | ADMIN | Change operational activation; deactivation forces OFFLINE |

Provisioning request:

```json
{
  "userId": 42,
  "profile": {
    "name": "Courier Name",
    "phone": "0771234567",
    "vehicleType": "MOTORCYCLE",
    "vehicleNumber": "ABC-1234",
    "area": "Colombo"
  }
}
```

PUT `/me` accepts just the `profile` object's fields. Vehicle types: BICYCLE, MOTORCYCLE, THREE_WHEELER, CAR, VAN, TRUCK. Availability request: `{"availability":"AVAILABLE"}`. Activation request: `{"active":true}`. List defaults to active profiles, page 0 and size 20; size is capped at 100. Availability is manually selected in this version; Dispatch must still check workload and active state when assigning.

Identity owns account creation and credentials. After creating a courier account, its owner must call this provisioning endpoint with authorized admin authentication, and retry idempotently if provisioning fails. An ACTIVE identity produces an active profile; a pending/inactive identity produces an inactive profile. Later profile activation verifies that the identity is ACTIVE and still a COURIER. The existing identity admin creation endpoint currently only supports ADMIN/DISPATCHER, so admin-created courier onboarding requires Member 5's integration change; this implementation does not silently modify identity-service or claim the callback is already wired. Identity suspension should also deactivate the courier profile; otherwise the existing JWT's account-status claim can remain stale until token expiry.

## Delivery APIs

All these endpoints require COURIER, an operational profile, and ownership of the relevant assignment. Read access may use a historical assignment; changes require a currently active assignment.

| Method and path | Behavior |
| --- | --- |
| `GET /api/couriers/me/deliveries?filter=today` | Today, Active or History; page/size pagination, Asia/Colombo date |
| `GET /api/couriers/me/deliveries/{parcelId}` | Assignment and parcel details |
| `POST /api/couriers/me/deliveries/{parcelId}/status` | JSON or multipart submission; updates parcel through its owner |
| `GET /api/couriers/me/delivery-submissions/{submissionId}` | Poll own submission confirmation state |
| `GET /api/couriers/me/deliveries/{parcelId}/tracking` | Own confirmed checkpoints, oldest first |
| `GET /api/couriers/me/deliveries/{parcelId}/proofs` | Own confirmed outcome records, preserving reattempts |
| `GET /api/couriers/me/deliveries/{parcelId}/proofs/{proofId}/photo` | Authenticated image; never a public storage directory |

Example status request:

```json
{
  "submissionId": "fb6454dc-f60f-4b6d-ae87-b977371ac832",
  "status": "DELIVERED",
  "recipientName": "Recipient Name",
  "note": "Handed over at reception",
  "locationDescription": "Reception desk",
  "latitude": 6.927079,
  "longitude": 79.861244,
  "accuracyMeters": 12
}
```

Generate a new submission UUID for each action, not for a retry. Allowed status values: PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED. Parcel-service is authoritative for whether a transition is allowed. Coordinates must come as a pair, latitude within [-90,90] and longitude within [-180,180], with at most six decimals. `capturedAt` may be an ISO-8601 instant no later than now. Without coordinates, collection/final outcomes require a nonblank locationDescription. GPS accuracy/time cannot be submitted alone.

DELIVERED requires a nonblank recipientName or a valid photo. FAILED requires failureReason: RECIPIENT_ABSENT, WRONG_ADDRESS, REFUSED, INACCESSIBLE_LOCATION, DAMAGED_PARCEL, OTHER. OTHER additionally requires a note. Reattempt authorization comes from Dispatch; `(parcelId, attemptNo)` is unique for a final outcome, so a later failed/successful attempt does not overwrite the previous one.

For multipart requests send `data` as an application/json part containing the same body, and `photo` as the optional file part. No arbitrary external photo URL is accepted. Proof responses construct a protected photoUrl rather than exposing a disk path.

### Confirmation and failure handling

The checkpoint and optional proof are committed locally before the remote parcel mutation. This is not a distributed database transaction. The remote request carries the same submissionId as `Idempotency-Key`. CONFIRMED returns HTTP 200; processing/unknown returns 202; a definite remote rejection returns 409 and releases the rejected final-proof slot. Rejected checkpoints are retained for audit but are not displayed as accepted tracking events. Unknown outcomes retain evidence for an authenticated retry after 30 seconds. Retry the identical payload, including the same photo, with the same submission ID; changed content returns 409. There is no unattended worker storing user tokens. A crash before confirmation can require retry even if the remote side applied the update.

Member 3 must implement idempotency and atomically validate assignment identity/version before accepting the remote status update. Without those downstream guarantees, local retries cannot provide exactly-once mutations or eliminate an assignment-change race. Ownership checks in courier-service still fail closed.

## Required external contracts (handoffs, not existing functionality)

The adapters forward the caller's JWT and use 3-second connection / 8-second read timeouts. APIs below must be agreed with their service owners. Dispatch/Parcel read and status endpoints are not implemented in the currently reviewed checkout; delivery integration will return an explicit error until those are supplied. Mocks are used only in tests.

1. Identity: existing `GET /api/admin/users/{userId}` returns `{id, role, status, ...}` to ADMIN. Add the admin-create-courier provisioning handoff described above.
2. Dispatch: proposed `GET /api/dispatch/couriers/{courierId}/assignments` returns a JSON array of `{id: UUID, parcelId: UUID, courierId: Long, attemptNo: positive integer, active: boolean, version: nonnegative integer, deliveryDate: YYYY-MM-DD, assignedAt: instant}`. It must authorize COURIER ownership and retain historical assignments. Reattempts have an increased attemptNo. The initial list fan-out is bounded at 200 assignments; agree pagination/bulk reads before larger workloads.
3. Parcel: proposed `GET /api/parcels/{parcelId}` returns `{id, trackingNumber, status, pickupAddress, deliveryAddress, receiverName, receiverPhone}` with ownership authorization. Proposed `POST /api/parcels/{parcelId}/status` accepts `{status, note, actorId, courierId, assignmentId, assignmentVersion, attemptNo, submissionId}` and `Idempotency-Key`. Return 200/204 only after commit, 409 on stale assignment/invalid transition; 202 is treated as not yet confirmed. Member 3 owns transitions, history and parcel-status event publication.
4. Gateway: existing `/api/couriers/**` route covers these endpoints. Agree gateway upload/request limits (5 MB file, 6 MB multipart request). Protect precise coordinates and proof photos; no public courier location endpoint is provided.

## Configuration and tests

Optional environment variables: `IDENTITY_SERVICE_URL`, `DISPATCH_SERVICE_URL`, `PARCEL_SERVICE_URL`, `COURIER_PROOF_DIRECTORY`. The default private storage directory is `.data/courier-proofs` relative to this service and is ignored by Git. Mount persistent storage when deploying; do not expose this folder as static content. RDS settings continue to come from the existing run-service script.

```bash
DB_PROFILE=rds bash infrastructure/scripts/run-service.sh courier-service
# From backend/courier-service:
./mvnw test
```

The original Flyway V1 is retained unchanged (checksum 1639178571); V2 adds the new courier profile, checkpoint and proof tables. The legacy couriers table and its three seeded demo records are preserved. Legacy UUID courier records are not automatically treated as authenticated numeric-user profiles; provision those through the profile API with verified identity accounts. Do not use Hibernate `update` against RDS. Unit and MVC authorization tests run without RDS; Testcontainers integration tests use throwaway PostgreSQL 18 and are skipped if Docker is unavailable.
