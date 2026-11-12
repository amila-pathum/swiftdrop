# Parcel creation

`POST /api/parcels` requires a valid Identity Service JWT with `role: CUSTOMER`
and a UUID subject. The subject supplies both `senderId` and `createdBy`.

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
