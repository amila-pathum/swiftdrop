-- Identity, Merchant and Courier use numeric IDs. Parcel/history IDs stay UUID.
-- Existing RDS databases with BIGINT references need no column changes.
-- Never guess an identity mapping or discard existing parcel/history records.
LOCK TABLE public.parcels, public.parcel_status_history IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    target RECORD;
    actual_type TEXT;
    has_rows BOOLEAN;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('parcels', 'sender_id'),
            ('parcels', 'created_by'),
            ('parcels', 'merchant_id'),
            ('parcels', 'assigned_courier_id'),
            ('parcel_status_history', 'changed_by')
        ) AS columns_to_align(table_name, column_name)
    LOOP
        SELECT data_type INTO actual_type FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = target.table_name AND column_name = target.column_name;
        IF actual_type = 'uuid' THEN
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I)', target.table_name) INTO has_rows;
            IF has_rows THEN
                RAISE EXCEPTION 'Cannot automatically convert %.% from UUID to BIGINT: existing records require an explicit identity mapping. No records were changed.', target.table_name, target.column_name;
            END IF;
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE BIGINT USING NULL::BIGINT', target.table_name, target.column_name);
        ELSIF actual_type IS DISTINCT FROM 'bigint' THEN
            RAISE EXCEPTION 'Unexpected reference type for %.%: %', target.table_name, target.column_name, actual_type;
        END IF;
    END LOOP;
END $$;
