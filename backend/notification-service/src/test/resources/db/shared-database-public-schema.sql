-- Objects another service could already have in the shared RDS database's
-- public schema. If Flyway or Hibernate used public instead of the service's
-- own schema, the RDS profile test would fail.
CREATE TABLE flyway_schema_history (installed_rank int PRIMARY KEY);
