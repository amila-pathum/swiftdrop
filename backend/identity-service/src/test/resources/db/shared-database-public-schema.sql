-- Objects another service could already have in the shared RDS database's
-- public schema. Their names clash with the Identity Service's on purpose: if
-- Flyway or Hibernate used public instead of identity, the test would fail.
CREATE TABLE users (id bigint PRIMARY KEY);
CREATE TABLE flyway_schema_history (installed_rank int PRIMARY KEY);
