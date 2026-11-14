# Event contracts

Messages that services publish to RabbitMQ. Describe each event before it is first published, as a JSON Schema file named `<domain>.<event>.v<version>.schema.json`, for example `parcel.status-changed.v1.schema.json`.

Conventions:

- All events go to one durable topic exchange, `swiftdrop.events`. The routing key is the event name, for example `parcel.status-changed`.
- Each consuming service declares its own durable queue, named `<service>.<event>` (for example `notification.parcel-status-changed`), bound to the routing keys it needs. Services never read another service's queue.
- An event states something that has already happened, named in the past tense (`parcel.status-changed`, not `update-parcel`).
- The publishing service owns the event and its schema. Consumers must ignore fields they do not know.
- Adding an optional field keeps the version. Any other change is a new version, published alongside the old one until all consumers have moved.
- Events carry identifiers and the data consumers need, never passwords, tokens or other secrets.


# Parcel Status Changed Event

## RabbitMQ Configuration

- Exchange: `swiftdrop.events`
- Exchange Type: `topic`
- Routing Key: `parcel.status-changed`

## RabbitMQ Queues

The Parcel Service publishes the event to the `swiftdrop.events` exchange.

The exchange routes a copy of each event to all three durable queues using the routing key `parcel.status-changed`.

| Consumer | Queue Name | Responsible Member |
|---|---|---|
| Notifications | `notification.parcel-status-changed` | Member 10 |
| Webhooks | `webhook.parcel-status-changed` | Member 9 |
| Dispatch | `dispatch.parcel-status-changed` | Member 7 |

Each consumer processes messages independently from its own queue.

## Event Payload

See [parcel-status-changed.json](./parcel-status-changed.json).

## Notes

- All consumers must use the same event contract.
- `occurredAt` uses UTC ISO 8601 format.
- `eventId` is a UUID.
- `courierId` may be null if no courier is assigned.

## Retry and Dead-Letter Policy

The Notification Service uses Spring AMQP listener retry
to handle temporary processing failures.

| Setting | Value |
|---|---|
| Retry enabled | Yes |
| Maximum attempts | 3 |
| Initial retry interval | 1 second |
| Retry multiplier | 2 |
| Maximum retry interval | 5 seconds |
| Requeue after exhaustion | No |

If all processing attempts fail, the message is rejected
and routed to the configured dead-letter exchange.

The dead-letter exchange routes failed messages to:

- Exchange: `swiftdrop.dlx`
- Routing key: `notification.failed`
- Queue: `notification.dlq`

Consumers in other services must configure their own
retry policies.

# Parcel Assigned Event

The Dispatch Service publishes `parcel.assigned` to the existing durable
`swiftdrop.events` topic exchange after its assignment transaction commits.
Its payload is defined in [parcel.assigned.v1.schema.json](./parcel.assigned.v1.schema.json).
The current Dispatch implementation updates its own demo projection; Parcel
Core has no assignment API yet, so this event does not imply that Parcel Core's
canonical `assigned_courier_id` has changed.

## Proposed creation-aware ParcelStatusChanged v2

[parcel.status-changed.v2.schema.json](./parcel.status-changed.v2.schema.json)
adds the creation convention without changing the existing v1 schema:

- `oldStatus` remains a required field.
- When `newStatus` is `CREATED`, `oldStatus` must be JSON null.
- Otherwise, `oldStatus` must be a nonblank string.
- Existing field names, identifier types and `occurredAt` are retained.
- This schema does not replace the Parcel Service transition validator.

**Rollout pending:** Notification/webhook and Dispatch owners must update their
validators to accept null only for creation. The webhook validator currently
rejects null before checking whether the parcel has a merchant. Dispatch's
`dispatch_report_events.old_status` persistence constraint must also be reviewed
for null support. Add consumer tests for creation, normal transitions, invalid
null previous statuses and duplicate event IDs.

The existing routing key remains `parcel.status-changed` for v1. No v2 routing,
header or payload version discriminator is agreed yet. Do not publish creation
events on that key until all bound consumers support them, or until an explicit
versioned routing and binding strategy is agreed. A schema file named v2 alone
does not let consumers distinguish versions at runtime.

Only the producer-owned contract is changed here; consumer implementations and
broker definitions remain unchanged. This is not confirmation from Members 7,
9 or 10 and does not complete the coordinated rollout.

Contract checks (from repository root):

```powershell
python -m pip install --target backend/parcel-service/target/schema-validation jsonschema==4.23.0
$env:PYTHONPATH = "backend/parcel-service/target/schema-validation"
python -m unittest discover -s backend/parcel-service/verification -p test_creation_event_contract.py
```
