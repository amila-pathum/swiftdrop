# Identity user IDs in Parcel Service

## Root cause and inspected state

Identity `User.id` is Java `Long` backed by PostgreSQL BIGINT.
`JwtService.generateToken` sets `sub` to `user.getId().toString()`, e.g. `"42"`.
A UUID parser cannot accept that subject, and UUID database columns cannot store
that Identity ID. Parcel/history primary keys and tracking-number generation
use legitimate UUIDs and remain unchanged.

This checkout already contained a partial fix: sender, creator and history actor
fields were `Long`, and V2 converted their columns **only on an empty database**.
Merchant/courier references and receipt ownership still used UUIDs. The controller
also contained an unfinished UUID helper declaration that prevented compilation.
This change completes that partial fix and repairs the controller.

Read-only inspection of the configured Parcel RDS database on 2026-11-13 found:

- Flyway V1 applied, checksum `-2085036544`.
- All five reference columns still UUID.
- `parcels`: 0 rows; `parcel_status_history`: 0 rows.

No configured/RDS database was changed. Re-run
`verification/preflight-identity-user-ids.sql` immediately before deployment.
V2 locks both tables before checking emptiness, preventing a concurrent insert
from being discarded during conversion.

## Identifier types

| Reference | Original V1 Java / PostgreSQL | Current Java / PostgreSQL |
| --- | --- | --- |
| senderId / sender_id | UUID / UUID | Long / BIGINT (already converted in V2 code) |
| createdBy / created_by | UUID / UUID | Long / BIGINT (already converted in V2 code) |
| changedBy / changed_by | UUID / UUID | Long / BIGINT (already converted in V2 code) |
| merchantId / merchant_id | UUID / UUID | Long / BIGINT |
| assignedCourierId / assigned_courier_id | UUID / UUID | Long / BIGINT |
| Parcel.id, history.id, history.parcel_id | UUID / UUID | unchanged |

Merchant repository parameters and `ParcelDetailsResponse.merchantId` now use
Long. Receipt ownership takes Long. Numeric values must be positive; incoming JWT
subjects must be canonical positive decimal Long values. Missing, UUID, negative,
zero, padded, signed or overflowing subjects are rejected with 401.

Courier Service has a **separate profile UUID**, with no authoritative Identity
mapping in its current model. Dispatch consumes that UUID as `courierId`.
The protected dispatch DTO therefore retains `courierId` as UUID and adds
`courierUserId` as Long. For legacy unresolved assignments it may expose the
preserved profile UUID; for numeric assignments `courierId` is null rather than
an invented UUID. Other services were inspected but not modified.

## Flyway and legacy data

V1 and V2 files/checksums are unchanged. New
`V3__complete_identity_user_references.sql` follows V2 and:

1. Locks `parcels` for the transaction.
2. Renames the old merchant/courier UUID columns to `legacy_merchant_uuid` and
   `legacy_assigned_courier_uuid`, preserving every value.
3. Adds nullable BIGINT `merchant_id` and `assigned_courier_id` with positive-value
   checks and indexes. Renames/retains the old merchant index for legacy lookup.
4. Leaves parcel IDs, tracking numbers, sender/creator IDs, history records,
   foreign keys, timestamps, existing required-column constraints and all other
   parcel data intact. No cross-service foreign keys or table drops are added.

On a fresh or empty V1 database, V1 → V2 → V3 runs normally. On a populated V2
database, V3 preserves all rows and stores unresolved UUIDs in legacy columns.
New numeric merchant/courier references remain null until independently verified.
They are not derived by casting, hashing, truncating, generating numbers or using
placeholder Identity accounts. New bookings/assignments store numeric IDs directly.

**Populated V1 databases remain blocked by the original V2 safeguard.** Without
an authoritative UUID-to-Identity mapping, this release intentionally cannot
complete their automatic upgrade. A test proves the failed upgrade rolls back,
preserving every original parcel/history field and keeping Flyway at V1.
Do not reset data, alter V2's checksum, baseline past V2, or run `repair` to conceal
this case. The inspected RDS database does not currently have this blocker.

For populated V1, stop writes and take a recoverable backup; obtain a verified
mapping for sender/creator/history actor UUIDs, and prepare an independently
reviewed offline upgrade that preserves original UUIDs and records mapping
provenance. If a mapping cannot be established, retain the original database
and do not release those parcels to numeric customer accounts. This migration
provides no automatic V1 ownership reconciliation tool.

For V3 legacy merchant/courier references, reconciliation must use an
operator-reviewed mapping from the owning service to Identity user IDs. Verify
Identity role/status, retain the source UUID and evidence, update only the exact
matching row while writes are controlled, and audit the operation. The staff
assignment endpoint can explicitly assign a known numeric courier user ID; it
does not claim to resolve a legacy profile UUID automatically.

## API behavior

- `POST /api/parcels`: existing CUSTOMER authorization retained. Sender and creator
  come only from the validated JWT. Body-supplied identity fields cannot override
  them. Customer creation cannot assign a courier or merchant.
- `POST /api/parcels/merchant`: ACTIVE MERCHANT authority required. Sender,
  creator and merchant references all use the authenticated Identity user ID.
  This is separate from the customer endpoint; it is not a merchant API-key or
  bulk-booking implementation.
- `PATCH /api/parcels/{parcelUuid}/courier`: DISPATCHER or ADMIN only, body
  `{"courierUserId":88}`. Positive numeric Identity ID required; closed parcels
  reject assignment. No public/customer/courier self-assignment route.
- Existing status endpoint: staff authorization retained; COURIER must match the
  stored numeric assignment. History `changedBy` is the JWT actor ID.
- Existing read, cancellation and receipt ownership remain numeric customer checks.
- Public `GET /api/parcels/track/{trackingNumber}` returns only public data without
  JWT, with real chronological history and no user IDs, contacts, addresses or
  internal notes. Unknown/invalid numbers return 404. Unsupported ETA/public
  location/description fields are null. Tracking format is unchanged.

The current checkout lacked the earlier tracking implementation and still had
its frontend availability flag disabled. The public DTO, endpoint and exact GET
security exception were restored; the frontend now consumes `trackingHistory`
and `timestamp` and enables the real endpoint. The gateway already forwards all
these paths; no gateway configuration change was required.

## Verification

- Parcel Service: **139 tests passed, none skipped**, using `./mvnw clean verify`,
  followed by final `./mvnw verify`.
  PostgreSQL integration tests cover migrations, numeric customer/merchant IDs,
  correct sender/creator/history persistence, missing/invalid JWT subjects,
  staff-only assignment, numeric courier authorization and public tracking.
- Fresh V3 database: all five reference columns verified BIGINT; parcel PK UUID.
- Populated V2 → V3: all non-reference parcel fields and every history field
  compared before/after; UUID references preserved; numeric references initially
  null; numeric assignment and positivity constraints verified.
- Populated V1: failed migration verified to preserve original data and version.
- Frontend: **415 tests passed** with `npm test -- --maxWorkers=2`;
  `npm run build` and `npm run lint` passed.
- Real Identity integration: `verification/identity-gateway-flow.py` passed.
  It starts actual Identity, Parcel and Gateway processes with disposable
  PostgreSQL, registers users, activates merchant/courier via the real admin API,
  logs in, verifies numeric JWT subjects, creates customer and merchant parcels,
  checks stored IDs, reads the parcel, assigns a courier as admin, updates status
  as the assigned courier and admin, verifies history, and tracks anonymously.
  Identity-injection attempts and unauthorized creation/assignment are checked.

The end-to-end test uses a **pricing HTTP stub only**. It verifies actual
Identity-issued JWTs and all Parcel/Gateway/database behavior, but does not claim
the independent Pricing contract/authentication integration is complete.
It never loads `.env` or uses configured databases, and cleans up its test
processes/container. Test fixtures are isolated; no production records are added.

Reproduce: package Identity and Gateway with `./mvnw -DskipTests package` in each
service directory, run `./mvnw verify` in Parcel Service, then from repository root:

```sh
python3 backend/parcel-service/verification/identity-gateway-flow.py
```

## Remaining boundaries

- No RDS deployment/migration or running-service restart was performed.
- Populated V1 migration requires the explicit reconciliation described above.
- Staff must supply a verified Identity courier ID. Automatic role/status lookup
  and profile-UUID mapping through Courier Service are not implemented. Courier
  mutation still requires a valid COURIER JWT and an exact numeric assignment.
- Dispatch can read its existing profile UUID field, but must adopt `courierUserId`
  or establish an authoritative mapping to act on new numeric assignments.
- ETA/public locations and tracking rate limiting remain separate work.
- Pricing was stubbed during the real Identity integration test.
- No Identity ID types/migrations or unrelated microservices were changed.
- No commits or pushes.

## Files created or modified

- `backend/parcel-service/IDENTITY_IDS.md`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/config/SecurityConfig.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/controller/ParcelController.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/dto/request/AssignCourierRequest.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/dto/response/DispatchParcelResponse.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/dto/response/ParcelDetailsResponse.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/dto/response/PublicTrackingResponse.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/entity/Parcel.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/repository/ParcelRepository.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/service/CourierAssignmentVerifier.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/service/ParcelCreationService.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/service/ParcelService.java`
- `backend/parcel-service/src/main/resources/db/migration/V3__complete_identity_user_references.sql`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/IdentityIdMigrationTest.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/ParcelCreationApiTest.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/ParcelReadApiTest.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/ParcelStatusUpdateApiTest.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/RdsProfileStartupTest.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/config/SecurityConfigTest.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/service/CourierAssignmentVerifierTest.java`
- `backend/parcel-service/verification/identity-gateway-flow.py`
- `frontend/src/App.test.jsx`
- `frontend/src/pages/public/TrackParcelPage.jsx`
- `frontend/src/services/endpoints.js`
- `frontend/src/services/endpoints.test.js`
- `frontend/src/services/parcelService.js`
