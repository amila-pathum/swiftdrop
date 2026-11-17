-- EXAMPLE DATA IN COURIER_DB ONLY. Supply verified real Identity IDs/parcel ID.
-- psql -d courier_db -U <courier_user> \
--   -v merchant_user_id=<merchant JWT subject> \
--   -v parcel_id=<new example parcel UUID> \
--   -v tracking_number=<unique example tracking number> \
--   -f backend/courier-service/demo/manual_merchant_example.sql
-- This does not create Identity accounts or write another service's database.
\set ON_ERROR_STOP on
\if :{?merchant_user_id}
\else
\echo 'Supply -v merchant_user_id=<verified MERCHANT Identity user ID>'
\quit 1
\endif
\if :{?parcel_id}
\else
\echo 'Supply -v parcel_id=<new example UUID>'
\quit 1
\endif
\if :{?tracking_number}
\else
\echo 'Supply -v tracking_number=<unique example tracking number>'
\quit 1
\endif

BEGIN;
DO $$ BEGIN
    IF current_database() <> 'courier_db' THEN
        RAISE EXCEPTION 'Run this example only against courier_db';
    END IF;
END $$;
CREATE TEMP TABLE courier_manual_example (
    merchant_user_id BIGINT NOT NULL CHECK (merchant_user_id > 0),
    parcel_id UUID NOT NULL,
    tracking_number VARCHAR(40) NOT NULL CHECK (length(btrim(tracking_number)) > 0)
) ON COMMIT DROP;
INSERT INTO courier_manual_example VALUES (:'merchant_user_id'::BIGINT,:'parcel_id'::UUID,:'tracking_number');

WITH inserted AS (
    INSERT INTO courier_delivery_parcels(parcel_id,tracking_number,merchant_user_id,status,status_at,
        pickup_address,delivery_address,receiver_name,receiver_phone,weight,size,delivery_date)
    SELECT parcel_id,tracking_number,merchant_user_id,'CREATED',now(),
        'Example pickup reception, Colombo','Example recipient reception, Colombo',
        'Example recipient','0770000000',1.000,'SMALL',(now() AT TIME ZONE 'Asia/Colombo')::date
    FROM courier_manual_example
    ON CONFLICT(parcel_id) DO NOTHING
    RETURNING parcel_id
)
INSERT INTO courier_delivery_history(id,parcel_id,status,recorded_at,attempt_no,actor_role)
SELECT gen_random_uuid(),parcel_id,'CREATED',now(),1,'MERCHANT' FROM inserted;

-- Fail rather than silently claim an existing record with another owner/name.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM courier_manual_example c JOIN courier_delivery_parcels p USING(parcel_id)
        WHERE p.merchant_user_id IS DISTINCT FROM c.merchant_user_id
           OR p.tracking_number IS DISTINCT FROM c.tracking_number
    ) THEN RAISE EXCEPTION 'Existing parcel does not match the verified import; nothing was overwritten'; END IF;
END $$;
SELECT p.parcel_id,p.tracking_number,p.merchant_user_id,p.status
FROM courier_delivery_parcels p JOIN courier_manual_example c USING(parcel_id);
COMMIT;
