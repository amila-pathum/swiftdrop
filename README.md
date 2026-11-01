# SwiftDrop

SwiftDrop is a university courier and delivery management system developed with a microservices architecture.

## Current architecture

```text
React frontend -> API Gateway -> Identity Service -> PostgreSQL
    :3000            :8080          :8081
```

The API Gateway routes requests and provides central CORS handling. JWT authentication and account authorization remain enforced by the Identity Service. The browser talks only to the Gateway.

RabbitMQ is planned but not yet part of the system; nothing needs to be installed or configured for it today.

## Services

| Service | Directory | Default port |
|---|---|---:|
| Frontend (React, Vite) | `frontend` | 3000 |
| API Gateway | `backend/api-gateway` | 8080 |
| Identity Service | `backend/identity-service` | 8081 |

Gateway routes preserve the original path:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google/exchange`
- `GET /api/users/me`
- `GET /oauth2/authorization/google` and `GET /login/oauth2/code/google` (Google sign-in browser redirects)

The Gateway health endpoint is `GET /actuator/health`. It reports Gateway application health only, not Identity Service or PostgreSQL health.

## Roles and authorization

Identity Service supports the roles `CUSTOMER`, `MERCHANT`, `COURIER`, and `DISPATCHER` (plus a reserved `ADMIN` value).

- Public registration (`POST /api/auth/register`) always creates an `ACTIVE` `CUSTOMER`. Any `role`, `status`, or similar fields in the request JSON are ignored.
- The JWT carries the user id (`sub`), `email`, and `role` claims. On every protected request the Identity Service reloads the user from the database and rejects the token with `401` if the account is not `ACTIVE` or if the email or role no longer matches the token. A user whose role changes must sign in again.
- The Spring Security authority is always `ROLE_<role>` taken from the current database record, never from request data.
- Method-level authorization is enabled (`@EnableMethodSecurity`), so endpoints can be restricted with, for example, `@PreAuthorize("hasRole('MERCHANT')")`. Authenticated users without the required role receive `403` with `{"error":"Access is denied"}`.
- `GET /api/users/me` returns only `id`, `name`, `email`, `role`, `status`, and `createdAt`; password hashes are never returned.

Limitation: there is currently no way to create `MERCHANT`, `COURIER`, `DISPATCHER`, or `ADMIN` accounts through the API, and no role-promotion endpoint. Privileged account provisioning requires a future secure administrative workflow. Until then, such accounts can only be prepared directly in the database by an operator.

## Prerequisites and environment

- Java 21
- Node.js 20.19+ or 22.12+ (for the frontend)
- PostgreSQL with a database `identity_db` owned by user `swiftdrop` (Flyway creates the tables)
- A Base64 JWT secret containing at least 32 random bytes for Identity Service, for example `openssl rand -base64 32`
- Docker, only for running the Identity Service database tests

Copy `.env.example` to `.env` in the repository root and fill in the values. `.env` is ignored by Git; never commit it.

Spring Boot does not automatically load the repository-root `.env`. Export its values, or source a shell-compatible file before starting the services:

```bash
set -a
source .env
set +a
```

The Gateway uses `GATEWAY_PORT`, `IDENTITY_SERVICE_URL`, and `FRONTEND_ORIGIN`. It does not need database credentials or the JWT secret.

The frontend does not read the root `.env`. Vite reads `frontend/.env.local` (ignored by Git), created from `frontend/.env.example`:

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | API Gateway URL used by the browser |
| `VITE_GOOGLE_LOGIN_ENABLED` | `false` | Shows "Continue with Google" when exactly `true` |

`VITE_` variables are embedded in the browser bundle, so they must never hold secrets. Restart `npm run dev`, or rebuild, after changing them.

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

In a third terminal, start the frontend at http://localhost:3000:

```bash
cd frontend
npm install
npm run dev
```

Stop any of them with `Ctrl+C`.

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

## Google sign-in (optional)

Google sign-in is off unless it is configured. Without it, email/password sign-in works as before and the Google endpoints redirect back with `#error=google_unavailable`.

1. In Google Cloud Console, create an OAuth client of type *Web application* with this authorized redirect URI (the API Gateway, not the Identity Service or the frontend):

   ```text
   http://localhost:8080/login/oauth2/code/google
   ```

2. Set both `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in the root `.env`, then restart the Identity Service. It refuses to start when only one of the two is set. Keep both values out of the frontend and out of tracked files.
3. Set `VITE_GOOGLE_LOGIN_ENABLED=true` in `frontend/.env.local` and restart the frontend. Enable this only when the backend is configured; otherwise users see "Google sign-in is not available right now".

`GOOGLE_REDIRECT_URI` overrides the default redirect URI. It must match the console exactly and keep the path `/login/oauth2/code/google`, which the Gateway routes to the Identity Service.

Environment variables:

| Variable | Where | Purpose |
|---|---|---|
| `GOOGLE_CLIENT_ID` | root `.env` | Google OAuth client ID; enables Google sign-in |
| `GOOGLE_CLIENT_SECRET` | root `.env` | Google OAuth client secret; required with the ID |
| `GOOGLE_REDIRECT_URI` | root `.env` | Optional; defaults to the URI above |
| `FRONTEND_ORIGIN` | root `.env` | Where the browser returns after Google, and the Gateway's allowed CORS origin |
| `VITE_GOOGLE_LOGIN_ENABLED` | `frontend/.env.local` | Shows the Google button |

Flow (OpenID Connect authorization code with PKCE; no token ever appears in a URL):

1. The frontend creates a random `verifier`, keeps it in the tab, and sends the browser to `GET /oauth2/authorization/google?client_challenge=<base64url SHA-256 of verifier>`.
2. After Google, the Identity Service redirects to `FRONTEND_ORIGIN/auth/google/callback#code=<one-time code>`, or `#error=<reason>` with `email_unverified`, `account_exists`, `account_inactive`, `google_failed` or `google_unavailable`.
3. The frontend posts `{"code": "...", "verifier": "..."}` to `POST /api/auth/google/exchange` and receives the same response as `POST /api/auth/login`. Codes are single-use, expire after 60 seconds, and are useless without the verifier.

Google sign-in creates an `ACTIVE` `CUSTOMER` without a password and requires a Google-verified email. It never links to an existing SwiftDrop account: if the email is already registered, the user is sent back with `account_exists` and must sign in with their password.

## Database migrations

Flyway manages the Identity Service schema (`backend/identity-service/src/main/resources/db/migration`), and Hibernate only validates it. An existing database created before Flyway is recorded as version 1 on first start and then migrated forward; an empty database runs every migration.

## Tests

Frontend unit and integration tests (Vitest, jsdom; no backend needed), lint and production build:

```bash
cd frontend
npm test
npm run lint
npm run build
```

API Gateway:

```bash
cd backend/api-gateway
./mvnw test
```

Gateway tests use an in-process mock upstream and require neither PostgreSQL nor a running Identity Service.

Identity Service:

```bash
cd backend/identity-service
./mvnw test
```

Identity Service database tests start PostgreSQL 18 with Testcontainers and need Docker; without Docker they are skipped. Google sign-in tests use a fake Google running inside the test and no real credentials. Docker is not needed to run the application.
