-- V2 already converted sender_id, created_by and changed_by to BIGINT.
-- Merchant/Courier UUIDs may be profile IDs rather than Identity user IDs.
-- Preserve them verbatim; no mathematically valid or authoritative conversion exists.
LOCK TABLE public.parcels IN ACCESS EXCLUSIVE MODE;
ALTER TABLE public.parcels RENAME COLUMN merchant_id TO legacy_merchant_uuid;
ALTER TABLE public.parcels RENAME COLUMN assigned_courier_id TO legacy_assigned_courier_uuid;
ALTER INDEX public.idx_parcels_merchant RENAME TO idx_parcels_legacy_merchant;
ALTER TABLE public.parcels
    ADD COLUMN merchant_id BIGINT,
    ADD COLUMN assigned_courier_id BIGINT,
    ADD CONSTRAINT chk_merchant_id_positive CHECK (merchant_id > 0),
    ADD CONSTRAINT chk_assigned_courier_id_positive CHECK (assigned_courier_id > 0);
CREATE INDEX idx_parcels_merchant ON public.parcels(merchant_id);
CREATE INDEX idx_parcels_assigned_courier ON public.parcels(assigned_courier_id);
COMMENT ON COLUMN public.parcels.legacy_merchant_uuid IS
    'Original pre-V3 UUID; requires authoritative profile-to-Identity mapping. Never exposed in public tracking.';
COMMENT ON COLUMN public.parcels.legacy_assigned_courier_uuid IS
    'Original pre-V3 UUID; numeric courier assignment remains NULL until explicitly resolved by authorized staff.';
