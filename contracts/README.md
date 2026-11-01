# Contracts

Agreements between SwiftDrop services and their clients, kept next to the code so that changes are reviewed together.

- `openapi/`: REST APIs exposed through the API Gateway
- `events/`: messages exchanged through RabbitMQ

A service team owns the contracts for its own service. Change a contract in the same pull request as the code that implements it, and ask the teams that consume it to review.
