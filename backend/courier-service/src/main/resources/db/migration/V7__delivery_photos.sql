-- Optional delivery photos at every status, stored in S3 (see DeliveryPhotoService).

-- The photo of one status update; NULL when the courier attached none.
ALTER TABLE delivery_checkpoints ADD COLUMN photo_key VARCHAR(200);

-- Keys handed to couriers for direct browser uploads. A key can be attached to
-- one update (used_at); unused keys and their files are removed after a day.
CREATE TABLE delivery_photo_uploads (
    photo_key VARCHAR(200) PRIMARY KEY,
    courier_id BIGINT NOT NULL REFERENCES courier_profiles(id),
    parcel_id UUID NOT NULL,
    content_type VARCHAR(20) NOT NULL CHECK (content_type IN ('image/jpeg','image/png')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    created_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);
CREATE INDEX idx_delivery_photo_uploads_unused ON delivery_photo_uploads (created_at) WHERE used_at IS NULL;

-- A delivery now always names its recipient; a photo is extra, not a substitute.
-- The V2 rule was unnamed, so find it by its definition. NOT VALID keeps older
-- photo-only deliveries while enforcing the rule for new rows.
DO $$
DECLARE rule_name TEXT;
BEGIN
    FOR rule_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'delivery_proofs'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%photo_key%'
    LOOP
        EXECUTE format('ALTER TABLE delivery_proofs DROP CONSTRAINT %I', rule_name);
    END LOOP;
END $$;
ALTER TABLE delivery_proofs ADD CONSTRAINT delivery_proofs_delivered_recipient
    CHECK (outcome <> 'DELIVERED' OR NULLIF(TRIM(recipient_name), '') IS NOT NULL) NOT VALID;
