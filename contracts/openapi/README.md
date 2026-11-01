# REST API contracts

One OpenAPI 3.1 document per service, named after the service directory, for example `parcel-service.yaml`. None exist yet; each team adds its document with its first endpoints.

Conventions:

- Paths are the public paths seen through the API Gateway, which forwards them unchanged. Each service owns one prefix:

  | Service | Prefix |
  |---|---|
  | Identity | `/api/auth`, `/api/users` |
  | Parcel | `/api/parcels` |
  | Merchant | `/api/merchants` |
  | Courier | `/api/couriers` |
  | Dispatch | `/api/dispatch` |
  | Pricing | `/api/pricing` |

  Notification Service is internal and has no public prefix.

- Request and response bodies are JSON with camelCase field names.
- Errors use the same body as the Identity Service: `{"error": "<message safe to show to users>"}`.
- Authenticated endpoints take `Authorization: Bearer <JWT>`, the token issued by the Identity Service. Document the roles each operation requires.
