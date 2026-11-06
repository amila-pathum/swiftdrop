-- Objects another service could already have in the shared RDS database's
-- public schema. If Flyway or Hibernate used public instead of the service's
-- own schema, the RDS profile test would fail.
CREATE TABLE flyway_schema_history (installed_rank int PRIMARY KEY);
-- Same name as the Parcel Service's table, different shape, one row.
CREATE TABLE parcels (id uuid PRIMARY KEY);
INSERT INTO parcels (id) VALUES (gen_random_uuid());
