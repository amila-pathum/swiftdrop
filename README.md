# SwiftDrop

SwiftDrop is a courier and delivery management system developed using a microservices architecture.

## Technology Stack

- React
- Java
- Spring Boot
- Spring Cloud Gateway
- PostgreSQL
- JWT
- BCrypt
- Docker
- Docker Compose

## Initial Services

- Frontend
- API Gateway
- Identity Service
- PostgreSQL

## Architecture

The initial system follows:

Frontend → API Gateway → Identity Service → PostgreSQL
# SwiftDrop

SwiftDrop is a courier and delivery management system developed using a microservices architecture.

## Technology Stack

- React
- Java
- Spring Boot
- Spring Cloud Gateway
- PostgreSQL
- JWT
- BCrypt
- Docker
- Docker Compose

## Initial Services

- Frontend
- API Gateway
- Identity Service
- PostgreSQL

## Architecture

The initial system follows:

Frontend → API Gateway → Identity Service → PostgreSQL

Each future microservice will own its own database and communicate through APIs or asynchronous events.

## Development

The root Docker Compose configuration will be the main development entry point.

```bash
docker compose up
Each future microservice will own its own database and communicate through APIs or asynchronous events.

## Development

The root Docker Compose configuration will be the main development entry point.

```bash
docker compose up