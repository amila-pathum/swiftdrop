# StyleCart

Separate clothing store: customer accounts, a catalogue and cart, persisted orders, an admin order desk, SwiftDrop shipment booking, signed webhook receipt and live order updates. StyleCart has its own accounts, encryption key and PostgreSQL database. It does not use SwiftDrop RDS for its data. Checkout is a demonstration with no payment collected.

## First start on Windows / Git Bash

Open Docker Desktop. Java 21 and Node.js/npm must be installed. Run from the SwiftDrop repository root:

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File stylecart/setup.ps1
cd stylecart
docker compose up -d --wait db
```

The script creates the ignored `stylecart/.env`, independent random database/admin credentials and settings encryption key, plus a localhost HTTPS certificate. Existing environment files are preserved. Open `stylecart/.env` locally to read `STYLECART_ADMIN_PASSWORD`. Keep this file and its encryption key; do not regenerate the key for an existing database.

In terminal 1, from repository root:

```bash
cd stylecart/backend
./mvnw spring-boot:run
```

In terminal 2, from repository root:

```bash
cd stylecart/frontend
npm ci
npm run dev
```

Open **http://localhost:3001**. The frontend proxies API calls to the local HTTPS backend. Register a customer to browse, place orders and view My Orders. Sign in with `admin@stylecart.local` and the generated admin password to open the order desk. These accounts are separate from SwiftDrop merchant accounts.

Ports: frontend 3001, HTTPS backend 8090, PostgreSQL 5440. The standalone shop can accept local orders while SwiftDrop is stopped; shipment booking requires the real SwiftDrop services.

## Connect SwiftDrop

In another Git Bash terminal, at the SwiftDrop repository root, before starting SwiftDrop:

```bash
source stylecart/backend/target/local-tls/swiftdrop-env.sh
DB_PROFILE=rds bash infrastructure/scripts/run-all.sh
```

If SwiftDrop is already running, stop it in its terminal first and restart with these exports. They allow the exact StyleCart HTTPS origin and trust its localhost certificate using a separate truststore. If other local webhook origins or JVM options are required, include those too. These commands do not edit SwiftDrop's `.env` or `.env.rds`.

1. In SwiftDrop (port 3000), use an approved merchant account and obtain its merchant API key.
2. In StyleCart admin, open Integration. Enter gateway `http://localhost:8080`, the merchant API key and pickup details. Save and check the connection.
3. In that same SwiftDrop merchant's webhook settings, set `https://localhost:8090/webhooks/swiftdrop`, enable the endpoint and save. Copy its **webhook signing secret** into StyleCart Integration and save. Do not use SwiftDrop's internal token or encryption key.
4. Send a test event from SwiftDrop. Inspect StyleCart's webhook activity and SwiftDrop Delivery History for success.
5. Place an order as a StyleCart customer. In StyleCart admin, book its shipment. A successful real SwiftDrop response supplies the tracking number.
6. Change that parcel's status through the authorized SwiftDrop workflow. Check the customer's My Orders and the admin order desk for the live update.
7. To test retries, select `Fail next two` in StyleCart Integration before a new status event. SwiftDrop's retry schedule controls the attempts; the first two retry delays are 1 and 5 minutes. Select Normal to clear forced failures.

The shop never generates substitute tracking numbers on an outage. API credentials remain on the backend. Component tests use an HTTP test double and are not proof of a live SwiftDrop/RDS demonstration.

## Stop

Press Ctrl+C in each app terminal. From `stylecart/`, stop only its database:

```bash
docker compose stop db
```

The database volume remains available for the next start. `mvnw clean` removes generated TLS files; rerun `setup.ps1` before starting, and restart SwiftDrop with the regenerated truststore exports if necessary.
