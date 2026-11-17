# Local courier sample data

## Current Merchant-owned workflow

The assignment authority was corrected to **MERCHANT**. Use [../API.md](../API.md)
for the current Courier-only API contract and [manual_merchant_example.sql](manual_merchant_example.sql)
for manual data preparation in `courier_db`. Merchants call Courier's assignment
API; couriers save activity locally and Courier publishes its own outbox events.
No Merchant code or database is changed.

The older Dispatcher fixture scripts/credentials below are retained as historical
test artifacts. Their Dispatcher assignment step is not authorized by the current
Courier API. Do not use those scripts as the Merchant integration contract. The
old read-only demo snapshots do not automatically confer merchant ownership or
become locally managed assignments; import verified ownership and use the new API.

## Current RDS demo

The user subsequently authorized RDS seeding and the targeted Dispatch compatibility
fix. **Use http://localhost:3000 for RDS**, with credentials from
`backend/courier-service/target/rds-demo/accounts.json`. This file is separate from
the local Docker credentials below; passwords are generated per environment.

Courier now has a RabbitMQ consumer and database projections. Its own V3 migration
adds explicit Dispatch UUIDs to authenticated courier profiles. Dispatch's RDS mode
resolves these UUIDs to the numeric profile/user IDs already used by its RDS V4
schema. Its `parcel.assigned` event carries a parcel snapshot containing addresses,
recipient contacts, weight and size. Courier does not call Dispatch or Parcel to
load assigned-delivery pages.

From the repository root, start each required service in its own terminal using
the existing code and the demo runtime wrapper:

```bash
bash backend/courier-service/demo/run_rds_service.sh identity-service
bash backend/courier-service/demo/run_rds_service.sh parcel-service
bash backend/courier-service/demo/run_rds_service.sh courier-service
bash backend/courier-service/demo/run_rds_service.sh dispatch-service
bash backend/courier-service/demo/run_rds_service.sh api-gateway
```

Wait for health on ports 8081/8082/8084/8085 and gateway 8080, then seed:

```powershell
python backend/courier-service/demo/seed_rds.py --apply
```

To populate the additional screenshot fields and replay the demo's canonical
history into the V4 Courier detail projection:

```powershell
python backend/courier-service/demo/seed_rds.py --details-only
```

This reads only the ten marked parcels and their actual status history, then
sends ten new immutable snapshot events through Parcel's existing outbox.
Pickup contact, Clothing contents, 25 x 20 x 10 cm dimensions, attempt numbers
and location labels are explicit **sample scenario data**, not inferred facts
about ordinary deliveries. Actor roles are mapped from the known fixture users.
Generated payloads/results are in `target/rds-demo/detail-snapshots.json` and
`detail-verification.json`; no existing identity password or canonical parcel
record is overwritten. The real detail page has been checked on desktop/mobile.

The RDS seed creates four marked demo identities, two linked demo courier profiles,
and a business profile for the existing active COURIER user 13 only if missing.
It preserves that existing account's password. Ten marked sample parcels include
seven initial assignments for demo Courier A, one for Courier B, one for user 13,
and an initially unassigned parcel for a real Dispatch API assignment check.
Only explicitly marked fixtures are inserted, with stable IDs and no overwrites.
Matching synthetic fixture assignment/status/snapshot events are published by the
existing Parcel outbox. The last assignment is made through Dispatch's actual API
and verified through Courier's actual gateway API, including another-courier denial.

`target/rds-demo/manifest.json`, `profiles.json`, `events.json` and `verification.json`
record the fixture and results. These and generated passwords are Git ignored.
The snapshot-only routing key is a demo handoff supported by the generic existing
Parcel outbox, not a new automatic production publisher in Parcel. Live assignment
details come from Dispatch's enriched assignment event. Reopen/refresh Courier's
Today or Deliveries page after dispatching; the existing frontend has no polling.
Unscheduled work assigned today appears in Today's queue without being given a
fabricated scheduled date. Explicit scheduled dates take precedence.

Limitations: no RabbitMQ status-write consumer exists in Parcel, so read-side
assignment integration does not enable Mark In Transit/delivery completion commands.
The bottom action opens the existing confirmation form. Cancellation events and
scheduled dates for normal parcels remain owner handoffs. Pickup contacts, contents,
dimensions and history metadata can now be stored/displayed by Courier, but their
automatic capture/publication by the owning services is still pending. The RDS
fixture supplies explicit sample values. Existing Dispatch reporting pages that require removed legacy
projection tables are outside the targeted assignment fix.

## Original local Docker setup

`seed_local.py` inserts sample records into the existing **local Docker** Identity,
Parcel, Courier and Dispatch databases. It does not modify service code, schemas,
Flyway migrations, RDS or existing business records. Run from the repository root:

```powershell
python backend/courier-service/demo/seed_local.py
```

Prerequisites: Docker containers `swiftdrop-postgres-1` and
`swiftdrop-rabbitmq-1`, their loopback port bindings, the existing service
migrations, Java with the Identity build's cached `spring-security-crypto` JAR,
and a running **local** Parcel Service to publish its outbox **to virtual host
`courier-demo`**. The script uses
Python's standard library and reads container credentials without printing them.

The local services started for this session use these isolated ports:

| Service | URL |
| --- | --- |
| Identity | http://localhost:18081 |
| Parcel | http://localhost:18082 |
| Courier | http://localhost:18084 |
| Dispatch | http://localhost:18085 |
| Demo API Gateway | http://localhost:18080 |
| Demo frontend | http://localhost:3001 |

Each was started using the existing `run-service.sh` with `DB_PROFILE=local`,
`POSTGRES_HOST=127.0.0.1`, `POSTGRES_PORT=5433`, the ports above, and matching
`*_SERVICE_URL` values. Parcel and Dispatch also use
`SPRING_RABBITMQ_VIRTUAL_HOST=courier-demo`. Logs are
`logs/local-demo-<service>.log`. Existing RDS
service processes were not repointed to local databases. The existing browser
and gateway at ports 3000/8080 do not automatically switch to these ports.

**Sign in to the demo at http://localhost:3001**, using an email and the generated
`password` value from `target/local-demo/accounts.json`. Those accounts exist in
local Docker Identity, not RDS. A password-mismatch message at the existing
http://localhost:3000 frontend can mean the account is being checked against the
RDS Identity database, where the demo email does not exist. Local account IDs
must never be treated as the same users as matching numeric IDs in RDS.

The separate demo gateway is started with `GATEWAY_PORT=18080`,
`FRONTEND_ORIGIN=http://localhost:3001` and service URLs on the local `1808x` ports.
The separate frontend is started with `VITE_API_BASE_URL=http://localhost:18080`
and `npm run dev -- --port 3001`. Merchant/Pricing/Notification URLs also use local
ports 18083/18086/18087; those services have not been started for this demo, and
their routes must not fall back to RDS-backed services. No frontend or gateway
source/configuration file is changed to start these instances.

## Records and accounts

- Eight clearly named parcels, tracking numbers `SD-COURIER-DEMO-001` through
  `SD-COURIER-DEMO-008`, with deterministic UUIDs, recipient contacts, addresses,
  weights, size categories, charges, status history and Dispatch assignments.
- The seeded subset for Courier A has three active and four completed parcels
  (three DELIVERED, one FAILED). Courier B has one active parcel for ownership
  testing. Pre-existing migration demo records are retained, so unfiltered
  Dispatch directory totals can include those older records too.
- Four ACTIVE local demo accounts: `courier-a@courier-demo.invalid`,
  `courier-b@courier-demo.invalid`, `customer@courier-demo.invalid` and
  `dispatcher@courier-demo.invalid`.
- Courier A/B profiles reference the actual inserted Identity user IDs. Fixture
  metadata records their intended association with the existing legacy demo
  courier UUIDs. **The application still has no persisted UUID/profile ownership
  link**; writing these sample profiles does not implement that link.

Generated files are under `backend/courier-service/target/local-demo/` (Git ignored):

| File | Contents |
| --- | --- |
| `accounts.json` | Generated local-only login password, account IDs, profile IDs and intended Dispatch UUIDs. Do not commit or publish this file |
| `manifest.json` | Full dataset and timestamps; fields unsupported by the schemas are explicitly under `unpersistedDisplayFields` |
| `events.json` | Exact existing-contract assignment and status payloads |
| `verification.json` | Verified seeded parcel/assignment counts and unique message count |

The unsupported fields are pickup contact, contents description, exact dimensions
and scheduled delivery date. They exist only as sample fixture values, not as
database records or newly supported event fields. No data is inserted into
Merchant, Pricing or Notification databases because these sample deliveries do
not require records there.

## RabbitMQ messages

The script declares an isolated **`courier-demo` virtual host**, with a durable
`swiftdrop.events` topic exchange, a durable inspection queue
`courier.demo.seed-events`, and the local Dispatch status queue. It does not
publish to the default `/` virtual host used by the RDS-backed services.
Prepare the virtual host before starting the local publisher/listener:

```powershell
python backend/courier-service/demo/seed_local.py --prepare-broker
```

Then start local Parcel/Dispatch with `SPRING_RABBITMQ_VIRTUAL_HOST=courier-demo`
and run the script without that flag to seed/publish:

- `parcel.assigned`: eight synthetic test events published through the local
  management API, matching the existing Dispatch payload contract. They do not
  pretend Dispatch executed a live assignment API operation.
- `parcel.status-changed`: 32 events inserted into Parcel's existing outbox in
  the same local transaction as each parcel and its history. The unmodified
  local Parcel Service publishes them normally.

The script verifies all 40 unique event IDs in **its own queue** using peek/requeue;
it never reads, purges or consumes another service's queue. Normal queues bound
to these routes **in the demo virtual host** also receive the test events. Default
virtual-host Notification, webhook and RDS-backed consumers are isolated from
these messages.

Reruns retain deterministic record/event IDs and existing timestamps. Inserts use
`ON CONFLICT DO NOTHING`, outbox events are not inserted twice, and assignment
messages can be redelivered with the same IDs. Consumers must deduplicate them.
Keep the generated manifest and account file when rerunning the same dataset.
Each database transaction is independent; a failure across databases is not a
distributed rollback. Fix the cause and rerun to complete the same fixture.

## What this does not implement

The original local fixture was prepared before the RabbitMQ implementation and
did not include a persisted UUID/profile association. Its old running processes
and sample profiles are not automatically upgraded/relinked by the RDS setup.
Use the RDS workflow above for the verified assigned-delivery integration.

No automatic cleanup or broad database deletion is performed. Any future cleanup
must target only the deterministic fixture UUIDs/accounts listed in the manifest.

During the initial setup, before isolation was corrected, the default-virtual-host
Notification consumer processed 32 sample status events against RDS. The resulting
32 demo notifications and 32 processed-event markers were backed up and removed
using exact fixture identifiers/timestamps. A follow-up audit confirmed zero matching
RDS records; the 40 matching messages were republished and verified in `courier-demo`.
Queued copies of those initial fixture messages were selectively removed from the
default virtual host, preserving unrelated messages. Repair audit files are in the
ignored `target/local-demo` directory. No other services' source code was changed.
