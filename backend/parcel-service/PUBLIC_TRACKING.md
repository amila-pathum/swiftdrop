# Public parcel tracking

`GET /api/parcels/track/{trackingNumber}` through the API Gateway (default
`http://localhost:8080`) requires no login, JWT or role. The frontend routes
`/track` and `/track/:trackingNumber` now use this endpoint.

Numbers are validated as 6–40 ASCII letters, digits or dashes, then compared
exactly, case-sensitively, with the stored tracking number. The existing generator
is unchanged: `SD` plus 32 uppercase hexadecimal UUID characters. The broader
validation preserves compatibility with existing valid legacy numbers. The
frontend retains its existing trim/uppercase input normalization.

A successful response is HTTP 200 with `Cache-Control: no-store`. Illustrative
response (values here are documentation examples, not application fallback data):

```json
{
  "trackingNumber": "SD1234567890ABCDEF1234567890ABCDEF",
  "status": "PICKED_UP",
  "createdAt": "2026-11-13T03:00:00Z",
  "estimatedDeliveryDate": null,
  "origin": null,
  "destination": null,
  "trackingHistory": [
    {
      "status": "CREATED",
      "description": null,
      "location": null,
      "timestamp": "2026-11-13T03:00:00Z"
    },
    {
      "status": "PICKED_UP",
      "description": null,
      "location": null,
      "timestamp": "2026-11-13T05:00:00Z"
    }
  ]
}
```

Unknown or malformed tracking numbers reaching the controller return HTTP 404:

```json
{"error":"Parcel not found"}
```

Missing path segments (`/api/parcels/track` or `/api/parcels/track/`) are outside
the exact public route and return 401 to anonymous callers. Other paths and
non-GET methods remain authenticated. Requests rejected earlier by the HTTP
server/security firewall can return 400 (for example unsafe encoded paths).

## Data and privacy

Uses the existing `ParcelRepository.findByTrackingNumber` and
`ParcelStatusHistoryRepository.findByParcelIdOrderByChangedAtAsc` methods.
Equivalent queries are:

```sql
SELECT * FROM parcels WHERE tracking_number = :trackingNumber;
SELECT * FROM parcel_status_history
WHERE parcel_id = :parcelId ORDER BY changed_at ASC;
```

The first uses the existing unique tracking-number constraint. The second uses
the `parcel_id` foreign key (`ParcelStatusHistory` has a lazy many-to-one
relationship with `Parcel`) and existing `(parcel_id, changed_at)` index.
A read-only repeatable-read transaction keeps current status and history in one
consistent PostgreSQL snapshot. No schema changes, tables or dependencies added.

History is mapped once per stored row, with no joins that multiply records,
synthetic events or inferred statuses. Recorded creation events are included;
empty history returns `[]`. Distinct stored rows are preserved, even if their
public values happen to match. Equal timestamps have no guaranteed relative order.
Current status comes from the parcel record, not the last history entry.

The public DTO is an explicit allowlist. It excludes IDs, sender/recipient names,
phone numbers, private addresses, ownership, charges, internal notes, actor IDs,
proofs and signatures. The current schema has no ETA or public origin,
destination, event description or event location fields: these return `null`.
Internal notes are deliberately not used as descriptions. No address parsing or
fabricated ETA is performed. The frontend can display an ETA if supported later.

Only `GET /api/parcels/track/{trackingNumber}` was added to `permitAll`.
The gateway already routes `/api/parcels/**` unchanged; no gateway configuration
change was necessary. Authorization remains enforced by Parcel Service.

Rate limiting was considered but is not implemented in this change. The current
UUID-based numbers have substantial entropy, validation bounds input, and errors
do not reveal ownership. Deploy an edge/gateway rate limit for public tracking
before exposure to sustained enumeration or high request volume; shared limits
must account for trusted proxies and multiple instances. No tracking-response or
personal-information logging was added.

## Files changed for this feature

Created:
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/dto/response/PublicTrackingResponse.java`
- `backend/parcel-service/src/test/java/com/swiftdrop/parcel/ParcelTrackingApiTest.java`
- `infrastructure/tests/public-tracking.py`
- `backend/parcel-service/PUBLIC_TRACKING.md`

Modified:
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/service/ParcelService.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/controller/ParcelController.java`
- `backend/parcel-service/src/main/java/com/swiftdrop/parcel/config/SecurityConfig.java`
- `backend/api-gateway/src/test/java/com/swiftdrop/gateway/GatewayIntegrationTest.java`
- `frontend/src/services/endpoints.js`
- `frontend/src/services/parcelService.js`
- `frontend/src/pages/public/TrackParcelPage.jsx`
- `frontend/src/App.test.jsx`
- `frontend/src/services/endpoints.test.js`

Existing unrelated working-tree edits were preserved. No commits or pushes made.

## Verification

- Parcel Service: 50 tests passed, including real PostgreSQL/Flyway tests.
- API Gateway: 27 tests passed, including anonymous tracking forwarding.
- Frontend: 377 tests passed (`npm test -- --maxWorkers=2`).
- Frontend production build passed.
- Real gateway → Parcel Service → disposable PostgreSQL smoke test passed:
  anonymous success, chronological history, privacy, unknown-number 404 and
  protected GET/POST rejection. This test uses isolated fixtures, never live
  customer data or application `.env` credentials, and cleans up its resources.

The database tests cover current status independently of history, empty history,
null ETA, reversed insertion order, creation events, unrelated parcels, exact
case matching, invalid lengths, missing numbers, anonymous access, protected
methods/subpaths and public response field allowlists.

Run backend tests in each service directory with `./mvnw test` (Docker required
for database tests). To repeat the full-stack smoke test, package both services
with `./mvnw -DskipTests package`, then from the repository root run:

```sh
python3 infrastructure/tests/public-tracking.py
```

## Remaining limitations

The previously identified Identity numeric `Long` IDs versus Parcel `UUID`
identifiers mismatch is still present in `User.id`, parcel sender/creator columns,
and `ParcelController.create`. It was not changed here. Public lookup of existing
parcels does not depend on Identity IDs; creation using numeric Identity JWT
subjects remains a separate integration issue. The smoke test therefore seeds
only its disposable test database, and does not claim the customer booking flow
is fixed. There is no new tracking-number format or production fixture data.

ETA/public locations/descriptions require a future authoritative data source.
Rate limiting requires deployment work as described above. Local verification
used isolated services; existing running deployments must be rebuilt/restarted
to serve the new endpoint.
