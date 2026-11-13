# Parcel creation

`POST /api/parcels` requires a valid Identity Service JWT with `role: CUSTOMER`
and a canonical positive numeric subject (a Java Long, matching Identity user IDs). The subject supplies both `senderId` and `createdBy`.

```json
{
  "receiverName": "Nimal",
  "receiverPhone": "0771234567",
  "pickupAddress": "Colombo",
  "deliveryAddress": "Kandy",
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

## Pricing contract (provisional)

The supplied Phase 2 HTTP adapter sends pickupAddress, deliveryAddress, weight,
size, and parcelType to POST `/api/pricing/quote` on `http://localhost:8086`.
It expects JSON such as `{"charge":450.00}`. Set `PRICING_SERVICE_URL` and
`PRICING_QUOTE_PATH` to override these defaults. The route, units, payload,
and any service authentication must be agreed with the pricing team; the
repository's pricing service does not yet implement this endpoint.

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
- Pricing: pending Member 4 confirmation. The pricing service currently has no
  quote controller. Confirm path, payload, weight units, currency, allowed sizes
  and types, response charge precision, and service authentication.
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
