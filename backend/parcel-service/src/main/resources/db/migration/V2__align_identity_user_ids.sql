-- Identity subjects are positive BIGINT user IDs. UUIDs cannot be mapped
-- to them without an authoritative mapping; never fabricate or discard IDs.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM parcels) OR EXISTS (SELECT 1 FROM parcel_status_history) THEN
        RAISE EXCEPTION 'Numeric Identity IDs require an explicit UUID-to-user-ID data migration for existing parcel records';
    END IF;
END $$;

ALTER TABLE parcels
    ALTER COLUMN sender_id TYPE BIGINT USING NULL::BIGINT,
    ALTER COLUMN created_by TYPE BIGINT USING NULL::BIGINT;
ALTER TABLE parcel_status_history
    ALTER COLUMN changed_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE parcels
    ADD CONSTRAINT chk_sender_id_positive CHECK (sender_id > 0),
    ADD CONSTRAINT chk_created_by_positive CHECK (created_by > 0);
ALTER TABLE parcel_status_history
    ADD CONSTRAINT chk_changed_by_positive CHECK (changed_by > 0);
