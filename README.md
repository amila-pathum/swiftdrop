# SwiftDrop

SwiftDrop is a university courier and delivery management system developed with a microservices architecture.

## Current architecture

```text
React frontend (future) -> API Gateway -> Identity Service -> PostgreSQL
                              :8080          :8081
```

The API Gateway routes requests and provides central CORS handling. JWT authentication and account authorization remain enforced by the Identity Service.

## Services

| Service | Directory | Default port |
|---|---|---:|
| API Gateway | `backend/api-gateway` | 8080 |
| Identity Service | `backend/identity-service` | 8081 |

Gateway routes preserve the original path:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`

The Gateway health endpoint is `GET /actuator/health`. It reports Gateway application health only, not Identity Service or PostgreSQL health.

## Prerequisites and environment

- Java 21
- PostgreSQL with the existing `identity_db`
- A Base64 JWT secret containing at least 32 random bytes for Identity Service

Spring Boot does not automatically load the repository-root `.env`. Export its values, or source a shell-compatible file before starting the services:

```bash
set -a
source .env
set +a
```

The Gateway uses `GATEWAY_PORT`, `IDENTITY_SERVICE_URL`, and `FRONTEND_ORIGIN`. It does not need database credentials or the JWT secret.

## Run locally

Start Identity Service:

```bash
cd backend/identity-service
./mvnw spring-boot:run
```

In another terminal, start the Gateway:

```bash
cd backend/api-gateway
./mvnw spring-boot:run
```

Stop either service with `Ctrl+C`.

## Verify through the Gateway

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Gateway Customer","email":"gateway@example.com","password":"SecurePass123!"}'

curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"gateway@example.com","password":"SecurePass123!"}'

TOKEN="$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"gateway@example.com","password":"SecurePass123!"}' | jq -r '.accessToken')"

curl -i http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```

## Tests

```bash
cd backend/api-gateway
./mvnw test
```

Gateway tests use an in-process mock upstream and require neither PostgreSQL nor a running Identity Service.
