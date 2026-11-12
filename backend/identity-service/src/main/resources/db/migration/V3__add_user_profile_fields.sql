-- Add customer profile fields.
-- Nullable columns preserve existing user records.

ALTER TABLE users
    ADD COLUMN phone varchar(30),
    ADD COLUMN address varchar(500);
