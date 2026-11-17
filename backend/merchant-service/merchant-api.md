# Member 9 merchant orders and webhooks

Public gateway origin: `http://localhost:8080` in local development.

| Method | Path | Authentication |
| --- | --- | --- |
| POST | `/api/merchants/integration/orders` | `X-API-Key` |
| GET | `/api/merchants/integration/orders?page=0&size=20` | `X-API-Key` |
| GET | `/api/merchants/integration/orders/{trackingNumber}` | `X-API-Key` |
| GET/PUT | `/api/merchants/me/webhook` | Approved merchant's JWT |
| POST | `/api/merchants/me/webhook/rotate-secret` | Approved merchant's JWT |
| GET | `/api/merchants/me/webhook/deliveries` | Approved merchant's JWT |
| GET | `/api/merchants/me/webhook/deliveries/{id}` | Approved merchant's JWT |
| POST | `/api/merchants/me/webhook/deliveries/{id}/retry` | Approved merchant's JWT |
| POST | `/api/merchants/me/webhook/deliveries/test` | Approved merchant's JWT |

Missing/invalid/revoked API key: 401. An unapproved merchant: 403. Wrong-owner order: 404. An already-used reference with different details: 409. Dependency outage: 503. Browser portal routes use the logged-in merchant's JWT; the integration order routes use an API key without a browser JWT.

## Order example

Save the following as a local `order.json` containing test contacts only. Do not commit real contact data:

```json
{
  "merchantOrderReference": "SHOP-DEMO-001",
  "pickup": {"name":"Demo shop","phone":"0112345678","address":"10 Main Street","city":"Colombo","postalCode":"00100"},
  "receiver": {"name":"Demo receiver","phone":"0771234567","address":"20 Lake Road","city":"Kandy","postalCode":"20000"},
  "parcel": {"description":"Clothing","weightKg":1.25,"lengthCm":35,"widthCm":25,"heightCm":12,"size":"MEDIUM","serviceType":"STANDARD","handlingNote":"Keep dry"}
}
```

District names must match Parcel Service's supported Sri Lankan districts. Optional `size` defaults to `MEDIUM`, and `serviceType` to `STANDARD`; dimensions are not used to invent a size classification. Parcel Service owns pricing, parcel validation, tracking generation, history and status events.

```bash
read -rsp 'Merchant API key: ' MERCHANT_API_KEY; echo
export MERCHANT_API_KEY
curl --fail-with-body http://localhost:8080/api/merchants/integration/orders \
  -H "X-API-Key: $MERCHANT_API_KEY" -H 'Content-Type: application/json' --data-binary @order.json
```

The response includes reference, tracking number, status, and `parcel` details (ID, charge, creation time, weight, size and service type). Repeating the exact request and reference returns the original parcel. After a timeout, retry the **same reference and unchanged body**. Never generate a new reference to retry an uncertain operation. The Parcel database transaction atomically deduplicates concurrent creates, including when Merchant Service has not saved the HTTP result yet.

The GET endpoints retrieve current status from the authenticated Parcel integration routes. Results remain scoped to the logged-in/key-owning merchant, including when different merchants use identical order references.

## Service boundary and ownership

1. Member 8's hashed key lookup authenticates an approved Merchant **profile ID**.
2. Merchant Service resolves its stored `userId`, never an identity supplied in the order body.
3. Identity's protected `POST /internal/merchant-order-tokens` issues a 60-second token only for an existing active MERCHANT account. It requires `MERCHANT_ORDER_SERVICE_TOKEN`, uses database-derived claims, and is not routed through API Gateway. Merchant Service never signs user tokens itself or returns the delegated token to the API caller.
4. Parcel's isolated `/api/parcels/merchant/integration/orders` endpoints require that Identity-issued JWT with the integration purpose and Parcel audience; an ordinary login JWT cannot bypass the API-key approval checks. Parcel V6 atomically reserves `(merchant_user_id, reference)`, creates the parcel through existing creation logic, writes history and an event containing the reference, and commits the mapping. A failure rolls back all of these writes.
5. Parcel events use **Identity user IDs**. Notification resolves `GET /internal/merchants/by-user/{userId}/profile` with the internal webhook credential before persisting under the Merchant **profile ID**. It never treats one identifier domain as the other. Lookup failure propagates to broker retry; an unknown profile is skipped. Broker-dead-lettered messages need the normal broker redrive procedure after dependency recovery.

Integration changes in Identity and Parcel require review from their owners before merge. Merchant migration V5 adds parcel response metadata. Parcel migration V6 adds its idempotency table. Commit/share the migrations with the code before starting against shared RDS; do not run Flyway repair or delete history to conceal a version mismatch.

## Secrets and local demo

`MERCHANT_ORDER_SERVICE_TOKEN`: a separate 32+ character service credential shared by Merchant and Identity. `INTERNAL_WEBHOOK_TOKEN`: Merchant ↔ Notification internal authentication. `WEBHOOK_ENCRYPTION_KEY`: Base64-encoded 32-byte AES key, shared by every Merchant instance that uses the same encrypted settings. These are distinct from merchant API keys and per-merchant webhook signing secrets. Real values belong only in ignored environment files.

See [`README.md`](../notification-service/src/test/resources/webhook/README.md) for the HTTPS mock receiver, signature checks, forced failures and Delivery History checks. The separate clothing store is documented in [StyleCart](../../stylecart/README.md).

## Webhook event subscriptions

`PUT /api/merchants/me/webhook` accepts `url`, `enabled`, and an optional `events` array containing any of `CREATED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`, `CANCELLED`.
GET returns the saved selection. An omitted array preserves an existing selection; new settings and existing rows migrated by Merchant V6 default to all seven statuses. An empty array sends no parcel status events, but enabled endpoints can still receive test events.

The worker checks the current selection before every delivery attempt, including retries. Unselected events are recorded as `SKIPPED` with `EVENT_NOT_SUBSCRIBED`; selecting that status later does not replay already skipped events. Deploy Merchant Service with V6 and Notification Service together to enable filtering. V6 only adds a column; it does not replace existing endpoints or signing secrets.

## Verification

- Merchant: `./mvnw test` from `backend/merchant-service`.
- Parcel: `./mvnw -Dtest=MerchantOrderIntegrationTest,ParcelCreationApiTest,RdsProfileStartupTest test`.
- Identity: `./mvnw -Dtest=MerchantTokenExchangeTest,JwtServiceTest test`.
- Notification: `./mvnw -Dtest=WebhookParcelStatusConsumerTest,HttpMerchantWebhookSettingsLookupTest,WebhookPipelineIntegrationTest test`.

Integration tests use throwaway databases/brokers; they do not migrate shared RDS. Tests prove component and HTTP contracts. A full live RDS/Gateway/browser demonstration must be observed separately; do not report it as passed from unit tests alone.
