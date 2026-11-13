-- Identity subjects are positive BIGINT user IDs. UUIDs cannot be mapped
-- to them without an authoritative mapping; never fabricate or discard IDs.
-- Run only after the read-only preflight confirms an empty UUID schema.
-- Flyway executes this migration in a transaction. Hold both locks until commit
-- so no insert can race the emptiness check and the column conversion.
LOCK TABLE public.parcels, public.parcel_status_history IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema = 'public' AND udt_name = 'uuid'
          AND ((table_name = 'parcels' AND column_name IN ('sender_id', 'created_by'))
            OR (table_name = 'parcel_status_history' AND column_name = 'changed_by'))) <> 3 THEN
        RAISE EXCEPTION 'Expected three UUID Identity reference columns; inspect schema and Flyway history before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM public.parcels) OR EXISTS (SELECT 1 FROM public.parcel_status_history) THEN
        RAISE EXCEPTION 'Numeric Identity IDs require an explicit UUID-to-user-ID data migration for existing parcel records';
    END IF;
END $$;

ALTER TABLE public.parcels
    ALTER COLUMN sender_id TYPE BIGINT USING NULL::BIGINT,
    ALTER COLUMN created_by TYPE BIGINT USING NULL::BIGINT;
ALTER TABLE public.parcel_status_history
    ALTER COLUMN changed_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE public.parcels
    ADD CONSTRAINT chk_sender_id_positive CHECK (sender_id > 0),
    ADD CONSTRAINT chk_created_by_positive CHECK (created_by > 0);
ALTER TABLE public.parcel_status_history
    ADD CONSTRAINT chk_changed_by_positive CHECK (changed_by > 0);
