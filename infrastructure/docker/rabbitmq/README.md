# RabbitMQ

RabbitMQ runs from the root `docker-compose.yml` (`rabbitmq:4-management`), prepared for event-driven integration between services. No service publishes or consumes events yet, so no exchanges or queues are defined here.

- AMQP: `localhost:${RABBITMQ_PORT:-5672}`
- Management UI: http://localhost:15672 (`RABBITMQ_MANAGEMENT_PORT`), signed in with `RABBITMQ_USER` / `RABBITMQ_PASSWORD` from the root `.env`

When the first events are introduced, put broker configuration that must exist before services start (for example a definitions file) in this directory, and describe each event in `contracts/events`.
