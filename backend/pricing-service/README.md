# Pricing & Zones

Pricing owns delivery zones, one active pricing rule per zone, and delivery fee quotes. Requests go through the gateway on port 8080 (service port 8086). Every endpoint requires an Identity JWT; mutations and `includeInactive=true` require ADMIN.

The complete API is in [pricing-service.yaml](../../../contracts/openapi/pricing-service.yaml) (repository path: `contracts/openapi/pricing-service.yaml`).

## Quote contract

With active Colombo and Kandy zones and an active Kandy rule (`baseFee=200`, `pricePerKg=150`, `expressMultiplier=1.5`):

```http
POST /api/pricing/quote
Authorization: Bearer <access-token>
Content-Type: application/json

{"pickupCity":"Colombo","deliveryCity":"Kandy","weight":2.5,"serviceType":"EXPRESS"}
```

```json
{
  "amount": 862.50,
  "currency": "LKR",
  "breakdown": {"baseFee": 200.00, "weightFee": 375.00, "expressFee": 287.50}
}
```

STANDARD returns 575.00 with expressFee 0.00. The delivery city's rule sets the price; both cities must have active zones. City matching ignores case and surrounding whitespace. Service type is normalized and validated before zone lookup. District fallback is intentionally not enabled: several cities can share a district, so a district needs an explicit unique mapping before it can select a rule reliably. The current booking form sends district names as cities; create corresponding city entries such as Colombo and Kandy.

Money is rounded HALF_UP to two decimal places. Express fee is the rounded total less the rounded base and weight fees, so the displayed components always sum to the amount. JSON clients may display 862.5 after parsing; format currency to two decimal places. The quote field is `amount`; the stored parcel field remains `charge`. Parcel-service recalculates the quote at booking time and persists that amount.

## Administration

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/zones`, `/api/pricing/rules` | Any authenticated user; active entries by default |
| GET | Same collection paths with `?includeInactive=true` | ADMIN |
| GET | `/api/zones/{id}`, `/api/pricing/rules/{id}` | Any authenticated user, including inactive entries |
| POST | `/api/zones`, `/api/pricing/rules` | ADMIN |
| PUT | `/api/zones/{id}`, `/api/pricing/rules/{id}` | ADMIN |
| PATCH | `/api/zones/{id}/disable`, `/api/pricing/rules/{id}/disable` | ADMIN |
| PATCH | `/api/zones/{id}/enable`, `/api/pricing/rules/{id}/enable` | ADMIN |
| POST | `/api/pricing/quote` | Any authenticated user |

Create zones with `name`, `city`, `district`. Create rules with `zoneId`, `baseFee`, `pricePerKg`, `expressMultiplier`; rule updates take the three numeric fields. Disabling a zone leaves its rules stored but makes quoting that city unavailable. Enable the zone before enabling a rule. Enabling an older rule while another rule is active returns 409; disable the replacement first.

Errors use `{"error":"message"}`. Invalid form fields return 400 with `{"error":"Please correct the highlighted fields","fields":{"weight":"Weight must be greater than zero (minimum 0.001 kg)"}}`. Unknown active cities and missing active rules return 404. Duplicate cities, duplicate active rules and conflicting reactivation return 409. Invalid service types return 400. Unauthenticated requests return 401; authenticated customers attempting admin operations return 403.

Migration V2 adds case-insensitive, whitespace-normalized city uniqueness and one active rule per zone. Existing databases with duplicate normalized cities or multiple active rules must have those conflicts resolved before migrating; this migration deliberately does not discard business data.

## Running and testing

Use the repository's local infrastructure setup and set `JWT_SECRET` (the same base64 key used by Identity) and `PRICING_DB_PASSWORD`. Optional defaults are `POSTGRES_HOST=localhost`, `POSTGRES_PORT=5433`, `PRICING_SERVICE_PORT=8086`. Do not point test commands at shared databases.

```sh
./mvnw spring-boot:run
./mvnw test
```

Docker is needed for PostgreSQL Testcontainers coverage. Tests cover strategy order, standard/express calculations, monetary rounding, validation/error JSON, role enforcement and persisted disable/enable/conflict behavior. Frontend estimates are implemented in `frontend/src/components/pricing/FeeEstimate.jsx`; input changes invalidate both the displayed quote and pending requests.

## Hand-off to Members 2 and 3

Send the sample above with this confirmation request:

> The quote contract is POST /api/pricing/quote with pickupCity, deliveryCity, weight and serviceType. Read amount (LKR), not charge or totalFee; the sample EXPRESS quote is 862.50. Member 2: please confirm the AI tool sends these fields and preserves amount in the model-facing result. Member 3: please confirm booking recalculates this quote and persists amount as parcel charge.

Repository check: Member 3's `HttpPricingClient` already reads `amount`. Member 2's HTTP client returns the JSON unchanged, but `app/agent/executor.py` still sends `pickupZone`, `dropoffZone`, `weightKg` without `serviceType`, and `app/agent/agent.py` filters results to `totalFee`/`currency`. The AI tool schema and tests must be updated along with that mapping. No AI-owner code was changed as part of this pricing implementation. Delivery of this hand-off and member confirmation remain pending contact details.
