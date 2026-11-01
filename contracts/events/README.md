# Event contracts

Messages that services publish to RabbitMQ. None exist yet. Describe each event before it is first published, as a JSON Schema file named `<domain>.<event>.v<version>.schema.json`, for example `parcel.status-changed.v1.schema.json`.

Conventions:

- All events go to one durable topic exchange, `swiftdrop.events`. The routing key is the event name, for example `parcel.status-changed`.
- Each consuming service declares its own durable queue, named `<service>.<event>` (for example `notification.parcel-status-changed`), bound to the routing keys it needs. Services never read another service's queue.
- An event states something that has already happened, named in the past tense (`parcel.status-changed`, not `update-parcel`).
- The publishing service owns the event and its schema. Consumers must ignore fields they do not know.
- Adding an optional field keeps the version. Any other change is a new version, published alongside the old one until all consumers have moved.
- Events carry identifiers and the data consumers need, never passwords, tokens or other secrets.
