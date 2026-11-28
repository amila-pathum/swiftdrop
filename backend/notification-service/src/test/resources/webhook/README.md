# Member 9 signed receiver demo

This receiver is separate from the deferred clothing-store app. It verifies the exact raw-body HMAC, a timestamp within five minutes, and the event header. It prints only event ID, tracking number, status and event time. Accepted event IDs are remembered until receiver restart.

## Prepare local HTTPS once (Windows)

From repository root, in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/notification-service/src/test/resources/webhook/prepare-demo.ps1
```

This creates a localhost certificate and copies the JDK's default CA truststore into ignored `target/webhook-demo`. It imports the demo certificate into that copy, preserving existing CA trust. It does not modify Windows or the JDK truststore. Use `-JavaHome "C:\path\to\jdk"` if Java cannot be discovered. Keep the generated private key local.

## Start SwiftDrop

Your ignored `.env` must have matching `WEBHOOK_ENCRYPTION_KEY`, `INTERNAL_WEBHOOK_TOKEN`, and `MERCHANT_ORDER_SERVICE_TOKEN`. All developers sharing the same encrypted webhook settings must use the same encryption key; exchange it privately, never in Git.

In Git Bash, repository root:

```bash
source backend/notification-service/target/webhook-demo/demo-env.sh
export SWIFTDROP_WEBHOOKS_CONSUMER_ENABLED=true
export SWIFTDROP_WEBHOOKS_WORKER_ENABLED=true
export SWIFTDROP_WEBHOOKS_ALLOWED_ORIGINS=https://localhost:9090
DB_PROFILE=rds bash infrastructure/scripts/run-all.sh
```

Keep the exported Java truststore options if you normally pass additional JVM options. Other HTTPS destinations require their exact origins in the comma-separated allowlist.

## Configure and run the receiver

1. Log into the SwiftDrop frontend with an approved merchant account.
2. Webhooks → set `https://localhost:9090/webhook`, enable it and save.
3. Copy the newly generated **webhook signing secret**. This is neither an API key nor the encryption key.
4. In a second Git Bash terminal at repository root:

```bash
source backend/notification-service/target/webhook-demo/demo-env.sh
read -rsp 'Webhook signing secret: ' WEBHOOK_SIGNING_SECRET; echo
export WEBHOOK_SIGNING_SECRET
node backend/notification-service/src/test/resources/webhook/receiver.mjs
```

5. Click **Send test** in SwiftDrop. Check receiver output and Delivery History; queue acceptance alone is not delivery success.
6. Enter `fail 2` in the receiver terminal. Create a real merchant API order (see `backend/merchant-service/merchant-api.md`), then change its status through the authorized Parcel workflow. The next two new valid attempts return 503; the third succeeds. History must show the attempts and growing delays.

Production retry delays are 1, 5, 15 and 60 minutes. The existing `WebhookPipelineIntegrationTest` exercises fail twice/succeed using a fake clock. Do not shorten production delays just to make a live demonstration faster. `success` clears forced failures; `quit` stops the receiver. A stopped receiver is also a real delivery failure.

```bash
node --test backend/notification-service/src/test/resources/webhook/receiver.test.mjs
```

No customer addresses or phone numbers belong in receiver output or webhook payloads. Share screenshots with secrets hidden.
