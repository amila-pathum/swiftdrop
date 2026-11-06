# SwiftDrop

SwiftDrop is a university courier and delivery management system built as a set of microservices. Customers and merchants book and track deliveries, dispatchers assign them to couriers, and couriers report delivery progress.

Authentication is complete: email/password and Google sign-in, JWT, role-based access, the API Gateway and the frontend authentication screens. The other services are skeletons that start, connect to their own database, verify JWTs and report health, ready for their teams to build on.

## Technology stack

| Area | Technology |
|---|---|
| Frontend | React 19, Vite, React Router, Vitest and Testing Library, ESLint |
| Backend services | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Flyway, Maven (wrapper per service) |
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Security | Spring Security; JWT (HMAC) issued by the Identity Service and verified by every service; Google OpenID Connect with PKCE |
| Data | PostgreSQL 18, one database per service |
| Messaging | RabbitMQ 4, prepared for events |
| Local infrastructure | Docker Compose |
| Tests | JUnit 5, Mockito, Testcontainers |

## Monorepo architecture

```text
swiftdrop/
├── frontend/                 React application for every role
├── backend/
│   ├── api-gateway/          single entry point for the frontend
│   ├── identity-service/     authentication, users, roles; issues JWTs
│   ├── parcel-service/
│   ├── merchant-service/
│   ├── courier-service/
│   ├── dispatch-service/
│   ├── pricing-service/
│   └── notification-service/ internal only, no Gateway route
├── infrastructure/
│   ├── docker/
│   │   ├── postgres/init/    creates each service's database and user
│   │   └── rabbitmq/
│   └── scripts/              run-service.sh, test-backend.sh
├── contracts/
│   ├── openapi/              REST API contracts and conventions
│   └── events/               RabbitMQ event contracts and conventions
├── docker-compose.yml        PostgreSQL and RabbitMQ for local development
├── .env.example              every environment variable, with placeholders
└── README.md
```

```text
Browser -> frontend (:3000)
              |
              v
         API Gateway (:8080) --> Identity (:8081)  --> identity_db
                             --> Parcel (:8082)    --> parcel_db
                             --> Merchant (:8083)  --> merchant_db
                             --> Courier (:8084)   --> courier_db
                             --> Dispatch (:8085)  --> dispatch_db
                             --> Pricing (:8086)   --> pricing_db
                                 Notification (:8087, internal) --> notification_db

         Services publish and consume events through RabbitMQ (:5672).
```

- Every service is an independent Maven project with its own wrapper, build and tests. There is no shared Java module.
- Every service owns one database and connects with its own database user, which cannot connect to any other service's database.

## Services, ownership and databases

| Service | Owns | Port | Database (DB user) | Gateway prefix |
|---|---|---:|---|---|
| API Gateway | Single entry point for the frontend; routing and CORS | 8080 | none | (entry point) |
| Identity | Authentication, Google OAuth, issuing JWTs, user profiles, roles and permissions | 8081 | `identity_db` (`swiftdrop`) | `/api/auth`, `/api/users`, Google sign-in redirects |
| Parcel | Parcel creation, tracking, status, status history | 8082 | `parcel_db` (`parcel_service`) | `/api/parcels/**` |
| Merchant | Merchant registration, admin approval, merchant API credentials, merchant orders, webhook configuration | 8083 | `merchant_db` (`merchant_service`) | `/api/merchants/**` |
| Courier | Courier profiles, availability, visibility of assigned deliveries, delivery status updates | 8084 | `courier_db` (`courier_service`) | `/api/couriers/**` |
| Dispatch | Courier assignment, dispatch management, delivery zones | 8085 | `dispatch_db` (`dispatch_service`) | `/api/dispatch/**` |
| Pricing | Pricing rules, zone-based pricing, delivery cost calculation | 8086 | `pricing_db` (`pricing_service`) | `/api/pricing/**` |
| Notification | Email notifications, parcel status notifications, merchant webhook delivery and retries | 8087 | `notification_db` (`notification_service`) | none: internal only |

If a feature seems to belong to two services, it belongs to the service that owns the data it changes. Agree on it in `contracts/` before building it.

Every service exposes `GET /actuator/health` on its own port, without a token and without details. A service with a database reports `UP` only when its database is reachable. The Gateway's health covers only the Gateway itself, not the services behind it. No other actuator endpoint is exposed.

## How services communicate

| From | To | How |
|---|---|---|
| Browser | API Gateway only | HTTPS/HTTP with the user's JWT |
| API Gateway | Identity, Parcel, Merchant, Courier, Dispatch, Pricing | Forwards each path prefix unchanged |
| Any service | Another service's REST API | Direct to that service's port (not through the Gateway), forwarding the caller's `Authorization` header |
| Any service | Other services, asynchronously | Events through RabbitMQ (`contracts/events`) |
| Notification | (receives work) | Only from events and backend calls; never from the browser |

Rules:

- **A service never reads or writes another service's database.** It asks the owning service through its API, or keeps its own copy of the data it needs, updated from that service's events.
- Use a REST call when you need an answer now (for example, a price quote while creating a parcel). Use an event to tell others that something happened (for example, a parcel status change that should trigger a notification).
- Describe every REST API in `contracts/openapi` and every event in `contracts/events` before or together with its implementation.
- Avoid call cycles between services; if two services need each other synchronously, raise it with both teams.

## Authentication and authorization

### Who does what

- **Identity Service issues JWTs** (`POST /api/auth/login`, `POST /api/auth/google/exchange`). It is the only service that signs tokens.
- **Every protected service verifies JWTs itself**, with the shared `JWT_SECRET`, in `config/SecurityConfig.java`. A request reaching a service is never trusted just because it came through the Gateway.
- **Every service enforces its own role rules** on its endpoints, with `@PreAuthorize`.
- **The API Gateway** only routes and handles CORS today. It may also check tokens in the future, as an extra layer, never as the only one.

### The token

The access token is a JWT signed with HMAC (`JWT_SECRET`, at least 32 random bytes), valid for `JWT_EXPIRATION_MS` (15 minutes by default). It carries:

| Claim | Meaning |
|---|---|
| `sub` | User id (as a string) |
| `email` | User's email |
| `role` | `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER` or `ADMIN` |
| `iat`, `exp` | Issued at and expiry |

Each domain service accepts a token only if the signature is valid, it has not expired, and it has `sub` and `role`. The role becomes the Spring Security authority `ROLE_<role>`. All services must use the same `JWT_SECRET`; `run-service.sh` loads it from `.env`.

### Security rules for developers

1. Every endpoint requires a valid token by default. The only public endpoint in a domain service is `GET /actuator/health`. Making anything else public needs a change to that service's `SecurityConfig` and a review.
2. Put `@PreAuthorize` on every controller method that should not be open to every signed-in role.
3. A role is not enough when data belongs to someone. Check ownership too: a customer may read only their own parcels, so compare the parcel's owner with `jwt.getSubject()`.
4. Take the user id and role only from the verified token, never from the request body, query parameters or custom headers.
5. Never sign tokens outside the Identity Service, even though other services know `JWT_SECRET`.
6. When calling another service on behalf of a user, forward the incoming `Authorization` header unchanged.
7. Error bodies are `{"error": "<message>"}`: `401` "Authentication is required" or "Invalid or expired access token", `403` "Access is denied". Never return stack traces or internal details.
8. Notification Service has no Gateway route; any HTTP endpoint it adds still requires a token.

Known limitations (decided by the team before production):

- The Identity Service checks the user's current status and role on every request. Other services cannot, so a token stays usable in them until it expires even if the account is suspended or its role changes. Keep `JWT_EXPIRATION_MS` short.
- Because the secret is shared, any service could technically sign tokens. Moving to asymmetric keys (Identity signs with a private key, services verify with the public key) would remove that risk.
- Service-to-service calls that are not on behalf of a user (for example, from a scheduled job) have no credentials yet. Prefer events for those until a service credential scheme is agreed.

### Roles in the Identity Service

- Public registration (`POST /api/auth/register`) always creates an `ACTIVE` `CUSTOMER`. Any `role`, `status`, or similar fields in the request JSON are ignored.
- On every protected request the Identity Service reloads the user from the database and rejects the token with `401` if the account is not `ACTIVE` or if the email or role no longer matches the token. A user whose role changes must sign in again.
- The Spring Security authority is always `ROLE_<role>` taken from the current database record, never from request data.
- Method-level authorization is enabled (`@EnableMethodSecurity`), so endpoints can be restricted with, for example, `@PreAuthorize("hasRole('MERCHANT')")`.
- `GET /api/users/me` returns only `id`, `name`, `email`, `role`, `status`, and `createdAt`; password hashes are never returned.

Limitation: there is currently no way to create `MERCHANT`, `COURIER`, `DISPATCHER`, or `ADMIN` accounts through the API, and no role-promotion endpoint. Privileged account provisioning requires a future secure administrative workflow. Until then, such accounts can only be prepared directly in the database by an operator.

## Creating a new API endpoint

Example: a merchant-only endpoint in the Merchant Service. The names are illustrative; design the real API in `contracts/openapi/merchant-service.yaml` first.

1. **Contract.** Add the operation to `contracts/openapi/<service>.yaml`, including the roles that may call it. Paths start with the service's Gateway prefix, so the Gateway already routes them.
2. **Request and response DTOs** as records in `dto/request` and `dto/response`, with Bean Validation on requests. Never return entities directly.
3. **Controller** in `controller/`, protected by a role, taking the user from the token:

   ```java
   @RestController
   @RequestMapping("/api/merchants")
   class WebhookController {

       private final WebhookService webhooks;

       WebhookController(WebhookService webhooks) {
           this.webhooks = webhooks;
       }

       @PutMapping("/me/webhook")
       @PreAuthorize("hasRole('MERCHANT')")
       WebhookResponse configure(@Valid @RequestBody WebhookRequest request,
               @AuthenticationPrincipal Jwt jwt) {
           long userId = Long.parseLong(jwt.getSubject());   // never from the request body
           return webhooks.configure(userId, request);
       }
   }
   ```

   `SecurityConfig` has already rejected requests without a valid token (`401`); `@PreAuthorize` rejects other roles (`403`). Use `hasAnyRole('MERCHANT', 'ADMIN')` for several roles, and check ownership in the service layer.
4. **Business logic** in `service/`, persistence in `entity/` and `repository/`, with a Flyway migration in `src/main/resources/db/migration`.
5. **Errors:** throw your own exceptions from `exception/` and map them to `{"error": "..."}` in a `@RestControllerAdvice` there.
6. **Tests:** follow `config/SecurityConfigTest` in any domain service. It signs tokens exactly as the Identity Service does and shows how to test `401`, `403` and the allowed role with MockMvc, without a database.
7. **Frontend:** call it through a module in `frontend/src/services/` using `apiClient.js`, for example `request('/api/merchants/me/webhook', { method: 'PUT', body, auth: true })`; `auth: true` attaches the stored token.

## Adding RabbitMQ events

No events exist yet. When the first one is needed:

1. **Contract.** Describe the event in `contracts/events/<domain>.<event>.v1.schema.json` (for example `parcel.status-changed.v1.schema.json`), following `contracts/events/README.md`.
2. **Dependency.** Add `spring-boot-starter-amqp` to the publishing and consuming services. Their health checks then also report RabbitMQ.
3. **Configuration** in each of those services' `application.properties`:

   ```properties
   spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
   spring.rabbitmq.port=${RABBITMQ_PORT:5672}
   spring.rabbitmq.username=${RABBITMQ_USER:swiftdrop}
   spring.rabbitmq.password=${RABBITMQ_PASSWORD}
   ```

4. **Publishing.** Publish to the topic exchange `swiftdrop.events` with the event name as routing key, only after the database transaction that caused it has committed (for example from a `@TransactionalEventListener(phase = AFTER_COMMIT)`).
5. **Consuming.** Each consumer declares its own durable queue, `<service>.<event>`, bound to `swiftdrop.events` with the routing keys it needs. Handlers must tolerate receiving the same event twice.
6. **Tests.** Use Testcontainers' RabbitMQ module, like the PostgreSQL tests.

## Frontend structure

One React application serves every role; routes and the user's role decide what is shown.

```text
frontend/src/
├── components/   shared UI and route guards (Button, FormField, ProtectedRoute, ...)
├── context/      AuthContext: session state, login, Google sign-in, logout
├── hooks/        shared React hooks
├── layouts/      AppLayout (signed in), AuthLayout (sign-in and status pages)
├── pages/
│   ├── auth/  customer/  merchant/  courier/  dispatcher/  admin/
├── services/     API client, auth and Google sign-in, token storage, config
├── utils/        framework-independent helpers (for example postLoginPath)
└── assets/       images and other static files imported by components
```

All API calls go through `services/apiClient.js` to the API Gateway, which attaches the stored token when called with `{ auth: true }`. Add a service module per backend domain (for example `services/parcelService.js`) rather than calling `fetch` from pages.

## Prerequisites

- Java 21
- Node.js 20.19+ or 22.12+
- Docker with Docker Compose, for PostgreSQL and RabbitMQ and for the database tests
- Git

## Local development setup

1. **Root environment file.** Copy it and fill in every placeholder:

   ```bash
   cp .env.example .env
   ```

   - `JWT_SECRET`: a Base64 secret of at least 32 random bytes, for example `openssl rand -base64 32`. Every service uses the same value.
   - `POSTGRES_PASSWORD` and `DB_PASSWORD` must be the same; the Identity Service connects as `POSTGRES_USER`.
   - Give each `*_DB_PASSWORD` and `RABBITMQ_PASSWORD` its own value, for example `openssl rand -base64 24`.
   - The API Gateway needs only `GATEWAY_PORT`, `FRONTEND_ORIGIN` and the `*_SERVICE_URL` values; it needs no database credentials or JWT secret.
   - `.env` is ignored by Git. Never commit it, and never put secrets in `.env.example`.

2. **Frontend environment file:**

   ```bash
   cp frontend/.env.example frontend/.env.local
   ```

   | Variable | Default | Purpose |
   |---|---|---|
   | `VITE_API_BASE_URL` | `http://localhost:8080` | API Gateway URL used by the browser |
   | `VITE_GOOGLE_LOGIN_ENABLED` | `false` | Shows "Continue with Google" when exactly `true` |

   `VITE_` variables are embedded in the browser bundle, so they must never hold secrets. The frontend does not read the root `.env`.

3. **PostgreSQL and RabbitMQ:**

   ```bash
   docker compose up -d                # or: docker compose up -d postgres
   docker compose ps                   # wait for "healthy"
   ```

   On first start, `infrastructure/docker/postgres/init` creates every service database and its user from the passwords in `.env`. It runs only while the data volume is empty; after changing a database password, run `docker compose down -v` (this deletes all local data) and start again. RabbitMQ's management UI is at http://localhost:15672 (`RABBITMQ_USER` / `RABBITMQ_PASSWORD`).

### Connecting to PostgreSQL

The container listens on port 5432 inside Docker and is published on the host at `POSTGRES_PORT`, 5433 by default, so it does not clash with a PostgreSQL installed on the machine.

| Where the client runs | Host | Port | Set in |
|---|---|---|---|
| On your machine: `mvnw spring-boot:run`, `run-service.sh`, IDE, `psql` | `localhost` | `5433` (`POSTGRES_PORT`) | root `.env` (the defaults) |
| Inside the Compose network (a future containerised service, or `docker compose exec`) | `postgres` | `5432` | that container's environment: `POSTGRES_HOST=postgres`, `POSTGRES_PORT=5432` |

```bash
psql -h localhost -p 5433 -U parcel_service parcel_db          # from your machine
docker compose exec postgres psql -U swiftdrop identity_db     # inside the container
```

Every service builds its JDBC URL from `POSTGRES_HOST` and `POSTGRES_PORT`, so changing them in `.env` moves all services at once. Database tests do not use either; Testcontainers starts its own PostgreSQL.

## Starting only what you need

Spring Boot does not read the root `.env` itself. `infrastructure/scripts/run-service.sh` loads it and starts one service; run each in its own terminal from the repository root:

```bash
infrastructure/scripts/run-service.sh identity-service
infrastructure/scripts/run-service.sh parcel-service
```

Variables already set in your shell take precedence over `.env`, for example `SPRING_PROFILES_ACTIVE=rds infrastructure/scripts/run-service.sh identity-service`.

| You are working on | Start |
|---|---|
| Frontend sign-in and profile | `postgres`, `identity-service`, `api-gateway`, frontend |
| A domain service's API (for example Parcel) | `postgres`, `identity-service` (to get tokens), your service; add `api-gateway` and the frontend to use it from the browser |
| A feature that calls another service | the above, plus the service you call |
| Events | the above, plus `docker compose up -d rabbitmq` and every service involved |

Each running Java service uses roughly 200–300 MB of memory, so start only what you need.

Without the script, load the variables yourself:

```bash
set -a; source .env; set +a
cd backend/parcel-service
./mvnw spring-boot:run
```

Frontend, at http://localhost:3000:

```bash
cd frontend
npm install
npm run dev
```

Stop a service with `Ctrl+C`, and the infrastructure with `docker compose stop`. Restart `npm run dev`, or rebuild, after changing `frontend/.env.local`.

Check that a service is up, and call a protected endpoint with a token:

```bash
curl http://localhost:8082/actuator/health      # {"status":"UP",...}

TOKEN="$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"gateway@example.com","password":"SecurePass123!"}' | jq -r '.accessToken')"

curl -i http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

Create that account first with `POST /api/auth/register` (`{"name": "...", "email": "...", "password": "..."}`).

## Running the Identity Service against AWS RDS

> **Shared database.** The RDS database `swiftdrop_dev` is used by the whole team. With the `rds` profile the Identity Service **applies its pending Flyway migrations on startup**, for everyone. Start it against RDS only from a branch whose migrations have been merged under the team's agreed process; develop and test new migrations against Docker or Testcontainers first. Never run `flyway clean`, drop the `identity` schema, or delete other people's data.

By default the Identity Service uses the local Docker PostgreSQL. The opt-in Spring profile `rds` (`backend/identity-service/src/main/resources/application-rds.properties`) connects it instead to the shared RDS database, in its own schema `identity`. No other service supports RDS yet.

What the profile does:

- Connects to `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require` as `DB_USERNAME`. Every connection, including Flyway's, is encrypted with TLS, and the driver refuses to connect without it. **The server certificate is not verified**: traffic cannot be read in transit, but a client would not detect an attacker impersonating the server. No CA certificate is needed.
- Flyway creates the `identity` schema if it is missing, keeps its history in `identity.flyway_schema_history` and runs V1 and V2 there; Hibernate validates and queries `identity`. Other services' objects in `public` are not touched.
- Baselining is off: if `identity` already contains tables but no Flyway history, startup fails instead of skipping `V1__baseline.sql`.
- A pool of at most 2 connections, closed after 2 minutes idle, because every developer and service shares one small instance.

### 1. Set the variables

In the root `.env` (they are ignored without the `rds` profile):

| Variable | Value |
|---|---|
| `DB_HOST` | RDS endpoint (ask the team; not stored in the repository) |
| `DB_PORT` | `5432` |
| `DB_NAME` | `swiftdrop_dev` |
| `DB_USERNAME` | RDS user (ask the team) |
| `DB_PASSWORD` | the RDS password: **do not put it in `.env`**, where `DB_PASSWORD` is the Docker password; enter it at run time (below) |

`JWT_SECRET` and the other Identity Service variables are read from `.env` as usual.

### 2. Start the service with the profile

`run-service.sh` lets variables already set in your shell win over `.env`, so the profile and the password apply to one run only:

```bash
read -rsp 'RDS password: ' DB_PASSWORD; echo; export DB_PASSWORD
SPRING_PROFILES_ACTIVE=rds infrastructure/scripts/run-service.sh identity-service
unset DB_PASSWORD                  # when done
```

`read -s` keeps the password out of your shell history and screen. Without `SPRING_PROFILES_ACTIVE=rds` the script starts the Docker setup exactly as before. The Docker `postgres` container is not needed for this service in `rds` mode. From an IDE, add `SPRING_PROFILES_ACTIVE=rds` and the `DB_*` variables to the run configuration's environment.

Check it: `curl http://localhost:8081/actuator/health` returns `{"status":"UP"}` once the database is reachable. Startup fails with `Could not resolve placeholder 'DB_…'` when a variable is missing. Outbound port 5432 must be allowed by your network.

### 3. Verify the schema in pgAdmin

1. *Register → Server*. **Connection**: host `DB_HOST`, port `5432`, maintenance database `swiftdrop_dev`, username `DB_USERNAME`; enter the password but do not tick *Save password* on a shared machine.
2. **Parameters** tab (pgAdmin 7+; *SSL* tab in older versions): *SSL mode* `require`.
3. Open *swiftdrop_dev → Schemas → identity → Tables*: `flyway_schema_history`, `oauth_login_codes`, `user_identities`, `users`.
4. In the Query Tool, the migration history should show V1 and V2 as `SQL` (not `BASELINE`):

   ```sql
   SELECT version, description, type, success FROM identity.flyway_schema_history ORDER BY installed_rank;
   ```

Tests never use RDS: they start their own PostgreSQL with Testcontainers. `RdsProfileStartupTest` runs the real `rds` profile against such a container to check the schema behaviour.

## Google sign-in (optional)

Google sign-in is off unless it is configured. Without it, email/password sign-in works as before and the Google endpoints redirect back with `#error=google_unavailable`.

1. In Google Cloud Console, create an OAuth client of type *Web application* with this authorized redirect URI (the API Gateway, not the Identity Service or the frontend):

   ```text
   http://localhost:8080/login/oauth2/code/google
   ```

2. Set both `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in the root `.env`, then restart the Identity Service. It refuses to start when only one of the two is set. Keep both values out of the frontend and out of tracked files. Google's downloaded `client_secret_*.json` files are ignored by Git.
3. Set `VITE_GOOGLE_LOGIN_ENABLED=true` in `frontend/.env.local` and restart the frontend. Enable this only when the backend is configured; otherwise users see "Google sign-in is not available right now".

`GOOGLE_REDIRECT_URI` overrides the default redirect URI. It must match the console exactly and keep the path `/login/oauth2/code/google`, which the Gateway routes to the Identity Service.

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

Each service's schema is managed by Flyway (`src/main/resources/db/migration` in that service), and Hibernate only validates it (`ddl-auto=validate`). Add a new `V<n>__<description>.sql` file for every schema change; never edit a migration that has already been merged.

For the Identity Service, a database created before Flyway is recorded as version 1 on first start and then migrated forward; an empty database runs every migration. With the `rds` profile, migrations run in the `identity` schema of the shared RDS database and baselining is off (see "Running the Identity Service against AWS RDS").

## Naming conventions

| Item | Convention | Example |
|---|---|---|
| Service directory and Maven `artifactId` | `<name>-service` | `parcel-service` |
| Java package | `com.swiftdrop.<name>` | `com.swiftdrop.parcel` |
| Application class | `<Name>ServiceApplication` | `ParcelServiceApplication` |
| Port variable | `<NAME>_SERVICE_PORT` | `PARCEL_SERVICE_PORT=8082` |
| Database and user | `<name>_db`, `<name>_service` | `parcel_db`, `parcel_service` |
| Database password | `<NAME>_DB_PASSWORD` | `PARCEL_DB_PASSWORD` |
| Gateway target | `<NAME>_SERVICE_URL` | `PARCEL_SERVICE_URL` |
| Public path prefix | `/api/<plural>` | `/api/parcels` |

The Identity Service predates these conventions and keeps its package `com.swiftdrop.identity_service`, database user `swiftdrop` and password variable `DB_PASSWORD`.

## Tests

Frontend unit and integration tests (Vitest, jsdom; no backend needed), lint and production build:

```bash
cd frontend
npm test
npm run lint
npm run build
```

All backend services, each built and tested with its own Maven wrapper:

```bash
infrastructure/scripts/test-backend.sh
```

One service:

```bash
cd backend/parcel-service
./mvnw test
```

- Database tests (the Identity Service's integration tests and every domain service's context test) start PostgreSQL 18 with Testcontainers and need Docker. Without Docker they are skipped, not failed, so check the skipped count.
- Security tests (`SecurityConfigTest` in each domain service, `SecurityApiTest` in the Identity Service) need neither Docker nor a database.
- Gateway tests use an in-process mock upstream and need no running service.
- Google sign-in tests use a fake Google inside the test and no real credentials.
- Tests do not read `.env` or `frontend/.env.local`. Run backend tests in a terminal where `.env` has not been sourced, because Spring also reads exported environment variables such as `GOOGLE_CLIENT_ID`.

## Git collaboration workflow

- `main` holds released, stable code. `develop` is the integration branch that all work merges into.
- Start each piece of work on a branch from an up-to-date `develop`, named `feature/<area>-<short-description>` (for example `feature/parcel-tracking`), or `fix/<area>-<short-description>` for bug fixes.
- Keep each branch focused on one service or one feature. Changes to another team's service, to `contracts/`, to the Gateway, to any `SecurityConfig`, or to shared frontend code (`components/`, `context/`, `layouts/`, `services/apiClient.js`) need a review from the owners of that code.
- Before opening a pull request, merge or rebase the latest `develop`, and run the tests, lint and build for everything you changed.
- Open a pull request into `develop` with a short description of what changed and how it was tested. Merge only after at least one approval and with all tests passing.
- `develop` is merged into `main` through a pull request when the team agrees a release is ready.
- Never commit `.env`, `frontend/.env.local`, credentials, `target/`, `node_modules/` or `dist/`. They are ignored by `.gitignore`; check `git status` before every commit.
