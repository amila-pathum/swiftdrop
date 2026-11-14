# Parcel creation

`POST /api/parcels` requires a valid Identity Service JWT with `role: CUSTOMER`
and a canonical positive numeric subject (a Java Long, matching Identity user IDs). The subject supplies both `senderId` and `createdBy`.

```json
{
  "receiverName": "Nimal",
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

Send `Authorization: Bearer <access-token>` and `Content-Type: application/json`.
Success returns HTTP 201 with `id`, `trackingNumber`, `status` (`CREATED`),
`charge`, and `createdAt`. Pricing determines the charge; callers cannot set
the sender, charge, tracking number, or status through the request DTO.
The parcel and its initial `null → CREATED` history entry commit together.

Validation covers required fields, column lengths, phone characters and at
least seven digits, and positive weight fitting NUMERIC(10,3).
Invalid requests return 400, missing/invalid authentication returns 401,
and other roles receive 403.

## Pricing contract and authentication

The HTTP adapter sends `pickupCity`, `deliveryCity`, `weight`, and `serviceType`
to `POST /api/pricing/quote` on `http://localhost:8086`. It reads `amount` from
the Pricing Service response; currency and fee breakdown remain Pricing-owned
response fields. Set `PRICING_SERVICE_URL` and `PRICING_QUOTE_PATH` to override
the defaults.

Parcel Service forwards the authenticated caller's JWT as a Bearer token on the
Pricing request. Both services independently validate the Identity-issued token.
The token is passed only in memory and is never stored in parcel data, logs, or
events. Pricing authentication remains enabled.

Unavailable pricing returns 503. Missing, negative, over-precision, or
out-of-range charges return 502. No substitute charge is generated.

## Tests

From this directory in PowerShell, run `.\mvnw.cmd test` with Docker running.
Database tests use disposable Testcontainers PostgreSQL, not `.env` or RDS.
For only the supplied creation unit test, run
`.\mvnw.cmd "-Dtest=ParcelCreationServiceTest" test`.

## Phase 2 verification (2026-11-13)

The creation implementation is already present: CreateParcelRequest, ParcelResponse,
PricingClient, HttpPricingClient, TrackingNumberGenerator, ParcelCreationService,
ParcelController, ParcelApiExceptionHandler, and ParcelCreationServiceTest.

- Compile and ParcelCreationServiceTest: passed.
- JWT contract: Identity JwtService emits role CUSTOMER and a decimal user ID
  subject, for example "42". Parcel now uses Long/BIGINT for senderId, createdBy,
  and changedBy. Signed-token regression tests cover this creation endpoint.
- Gateway: GatewayIntegrationTest passes, including POST /api/parcels.
- Pricing: the repository implements `POST /api/pricing/quote`. Parcel forwards
  the caller JWT and uses the agreed city, weight and service-type request.
- Postman: collection prepared in verification/parcel-creation.postman_collection.json;
  not executed. Import it and set accessToken locally to a real CUSTOMER token.
  baseUrl defaults to the gateway on port 8080. A successful request stores parcelId
  and trackingNumber as collection variables. Each execution creates a record.
- PostgreSQL: not verified in this run; Docker engine was unavailable, so
  Testcontainers tests were skipped. After a successful Postman request, run
  verification/verify-created-parcel.sql in parcel_db with the returned parcelId.
  Confirm sender_id/created_by/changed_by match the JWT subject, charge matches
  Pricing, and exactly one initial null -> CREATED history entry exists.

V2 preserves the V1 migration checksum and changes only sender and audit user IDs.
It stops on a populated legacy database because UUIDs have no intrinsic numeric
Identity mapping. Such databases need an explicit data migration based on the
real Identity mapping before this change can be deployed. Parcel, history,
merchant, and courier entity identifiers retain their existing UUID contracts.

## Creation API test matrix

Run `.\mvnw.cmd "-Dtest=ParcelCreationApiTest,HttpPricingClientTest,SecurityConfigTest,ParcelCreationServiceTest" test`
with Docker running. ParcelCreationApiTest requires Docker and fails instead of
silently skipping when it is unavailable. It uses disposable PostgreSQL and mocked
pricing; successful creation and the shared request helper use signed CUSTOMER JWTs.

Coverage includes required receiver/address fields, phone format, weight limits,
unsupported size/type, authentication/authorization, pricing failures, invalid
charges, frontend charge tampering, duplicate tracking (409), history insertion
rollback, and exactly one parcel plus one initial history row on success.

The supported values are SMALL/MEDIUM/LARGE and STANDARD/EXPRESS.

Real end-to-end verification through Gateway remains pending. Start the services,
configure active pickup/delivery zones and a pricing rule, obtain a CUSTOMER token
from Identity, run verification/parcel-creation.postman_collection.json through
Gateway, then run verification/verify-created-parcel.sql with the returned parcelId.
The automated Parcel tests mock Pricing and therefore do not replace this live check.

## Phase 3 verification

Run `.\mvnw.cmd "-Dtest=ParcelStatusUpdateApiTest,ParcelStatusUpdateServiceTest,ParcelStatusTransitionValidatorTest,SecurityConfigTest" test`.
The API suite requires Docker and sends real HTTP requests to a random-port server
with cryptographically signed test JWTs (not tokens acquired from a running Identity
Service). PostgreSQL is disposable; no development or team database is modified.
It checks ADMIN/DISPATCHER updates, forbidden roles/unverified couriers, missing JWT,
invalid transitions and cancellation, rollback after flushing parcel/history writes,
and concurrent updates yielding one 200, one 409, and only one new history record.

Courier authorization remains incomplete. Member 6 must confirm the authoritative
mapping from numeric Identity user IDs to assigned courier UUIDs, including endpoint,
authentication, missing/disabled courier semantics, and failure handling. The current
verifier denies courier changes. Once confirmed, implement the trusted lookup and
test matching, nonmatching, absent, disabled, and unavailable mappings before enabling
courier updates. The existing service unit test mocks a positive verifier result;
it is not evidence that the production mapping exists.

## Merge compatibility: dispatch HTTP projection

The Phase 4 `/api/parcels?status=CREATED` endpoint retains its paginated details
response. The incoming Dispatch Service expects an array of dispatch projections;
that separate contract is available at `/api/parcels/dispatch?status=CREATED`
and `/api/parcels/dispatch/{parcelId}`. Both require DISPATCHER or ADMIN.

For Dispatch Service's optional HTTP data source, set its existing environment
settings before startup (no Dispatch Service source changes are required):

```text
PARCEL_CREATED_PATH=/api/parcels/dispatch?status=CREATED
PARCEL_PATH=/api/parcels/dispatch/{parcelId}
```

Customer cancellation is retained at `PATCH /api/parcels/{id}/cancel`, using the
numeric Identity subject. Existing paginated customer reads and Phase 3 status
updates retain their original contracts.
