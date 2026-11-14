# Pricing & Zones

Pricing owns delivery zones, the published delivery rates, and delivery fee quotes. Requests go through the gateway on port 8080 (service port 8086). Every endpoint requires an Identity Service JWT.

The complete API is in [pricing-service.yaml](../../contracts/openapi/pricing-service.yaml).

## How a price is worked out

1. **Zones.** An admin creates zones, each with a name, a short code (for example `WP`) and the localities (cities) it covers. A locality belongs to exactly one zone. Customers pick these localities as their pickup and delivery cities.
2. **Rates.** For every pair of zones (including a zone with itself) the admin sets a base weight, a base fee, a fee for each additional kg, and an express multiplier. A pair costs the same in both directions. There is also one surcharge per parcel size: Small, Medium, Large.
3. **Versions.** Rates are edited as a draft on the Pricing page and published together as a new version (v1, v2, …). Published versions are never changed, and quotes use the latest one. Parcels keep the charge they were booked with.
4. **Quote.** Each city is turned into its zone, the rate for that pair is looked up, and the pricing strategies run in order:

| Order | Strategy | Breakdown key | Adds |
| --- | --- | --- | --- |
| 1 | `ZoneBasedPricingStrategy` | `baseFee` | the route's base fee |
| 2 | `WeightBasedPricingStrategy` | `weightFee` | each started kg above the base weight × the additional kg fee |
| 3 | `SizeSurchargePricingStrategy` | `sizeFee` | the size band's surcharge |
| 4 | `ExpressPricingStrategy` | `expressFee` | for EXPRESS, (multiplier − 1) × everything before it |

`PricingCalculator` runs every `PricingStrategy` bean in `@Order` and records what each one added, so a new rule is a new class and the existing ones stay unchanged. Each step is rounded HALF_UP to cents, so the breakdown always adds up to the amount.

## Quote contract

With Western Metro covering Colombo, Central Hills covering Kandy, and their route at base weight 1 kg, base fee 350, 100 per additional kg, express ×1.5, and a Medium surcharge of 150:

```http
POST /api/pricing/quote
Authorization: Bearer <access-token>
Content-Type: application/json

{"pickupCity":"Colombo","deliveryCity":"Kandy","weight":2.3,"serviceType":"EXPRESS","size":"MEDIUM"}
```

```json
{
  "amount": 1050.00,
  "currency": "LKR",
  "breakdown": {"baseFee": 350.00, "weightFee": 200.00, "sizeFee": 150.00, "expressFee": 350.00}
}
```

2.3 kg is 2 started kg above the 1 kg base weight. The same parcel as STANDARD and SMALL costs 550.00. Cities match zone localities ignoring case and surrounding spaces. `serviceType` (STANDARD or EXPRESS) and `size` (SMALL, MEDIUM or LARGE) are case-insensitive and are checked before any lookup.

The quote's total is `amount`. Parcel-service sends `size` with each quote, recalculates the quote at booking time, and stores the amount as the parcel's `charge`.

## Endpoints

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/zones` | Any signed-in user: active zones. `?includeInactive=true` is ADMIN only |
| GET | `/api/zones/{id}` | Any signed-in user |
| POST, PUT | `/api/zones`, `/api/zones/{id}` | ADMIN. Body `{name, code, localities: [...], active}` |
| PATCH | `/api/zones/{id}/disable`, `/api/zones/{id}/enable` | ADMIN |
| GET | `/api/pricing/rates` | ADMIN. Current version, with a row for every pair of active zones |
| POST | `/api/pricing/rates` | ADMIN. Publishes the next version |
| GET | `/api/pricing/rates/versions` | ADMIN. Version history |
| POST | `/api/pricing/quote` | Any signed-in user and other services |

Publishing sends `basedOnVersion` (the version the admin loaded, or null before the first publish). If someone published in between, the request returns 409 instead of overwriting their rates. Pairs left out of a publish cannot be booked. Rates for pairs that involve a disabled zone are carried over automatically, so enabling the zone again restores its prices.

## Errors

Errors use `{"error":"message"}`.

| Status | When |
| --- | --- |
| 400 | Invalid fields, listed under `fields` (for example `{"weight": "...", "routes[2].baseFee": "..."}`); unknown service type or size; publishing a disabled zone or the same pair twice |
| 401 | No token, or an invalid one |
| 403 | Signed in, but not an ADMIN, for admin endpoints |
| 404 | No active zone covers a city, nothing is published yet, the route has no rate, or the zone id is unknown |
| 409 | Zone code or a locality already belongs to another zone, or a newer rate version was published |

## Database

Flyway owns the schema. V4 replaced each zone's single city and district with `zones.code` and the `zone_localities` table, and replaced per-zone `pricing_rules` with `rate_versions` and `route_rates`. Existing zones keep their city as their first locality and get a placeholder code (`Z` plus 6 characters of the id) for an admin to rename.

The shared RDS database also has an out-of-repo V2, "add zone code and districts". This repository therefore skips V2, and the `rds` profile sets `spring.flyway.ignore-migration-patterns=*:missing`. V4 works whether or not that V2 ran: it reuses an existing `code` column, and copies localities from its `zone_districts` table when the table exists (skipping any locality already in another zone). It leaves `zone_districts` itself in place.

V5 seeds the zones: Sri Lanka's nine provinces (codes WP, CP, SP, NP, EP, NWP, NCP, UP, SGP), each covering its districts' capital cities, 25 in all. It replaces any zones created before it, along with any rates that referred to them (rates must be published for the provinces). Admins can add more cities afterwards on the Delivery Zones page.

## Running and testing

Set `JWT_SECRET` (the same base64 key Identity uses) and `PRICING_DB_PASSWORD`. Optional defaults are `POSTGRES_HOST=localhost`, `POSTGRES_PORT=5433` and `PRICING_SERVICE_PORT=8086`. Never point tests at a shared database.

```sh
./mvnw spring-boot:run
./mvnw test
```

After renaming or deleting a migration, run `./mvnw clean` so that no stale copy stays in `target/classes`.

Docker is needed for the PostgreSQL Testcontainers tests. The tests cover:
- each strategy and the calculator: ordering, rounding, a newly added strategy;
- quote validation and errors;
- ADMIN-only access, including the 403 responses;
- the full zone → publish → quote flow, including stale publishes and disabled zones;
- the V4 and V5 data migrations.

Frontend:
- `frontend/src/components/pricing/FeeEstimate.jsx` is the reusable estimate used by Create Delivery.
- `frontend/src/pages/admin/ZoneManagementPage.jsx` and `frontend/src/pages/admin/PricingPage.jsx` are the admin pages.

## Hand-off to Members 2 and 3

> The quote contract changed: `POST /api/pricing/quote` now also needs `size` (SMALL, MEDIUM or LARGE), and the breakdown gained `sizeFee`. Cities must be localities of an active zone; `GET /api/zones` lists them. The total is `amount` (LKR). Sample: Colombo → Kandy, 2.3 kg, EXPRESS, MEDIUM = 1050.00.
>
> Member 3: parcel-service's `HttpPricingClient` now sends `size` (changed in this PR); please review.
>
> Member 2: the chatbot's `estimate_fee` tool (`app/agent/executor.py`) still sends `pickupZone`, `dropoffZone` and `weightKg`, and `app/agent/agent.py` reads `totalFee`. It needs `pickupCity`, `deliveryCity`, `weight`, `serviceType` and `size`, and should read `amount`.
