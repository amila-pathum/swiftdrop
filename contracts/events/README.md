# Event contracts

Messages that services publish to RabbitMQ. None exist yet. Describe each event before it is first published, as a JSON Schema file named `<domain>.<event>.v<version>.schema.json`, for example `parcel.status-changed.v1.schema.json`.

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
| Reports | `reports.parcel-status-changed` | Member 7 |

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

