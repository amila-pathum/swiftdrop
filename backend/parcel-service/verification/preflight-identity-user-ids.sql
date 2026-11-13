-- Run against the intended parcel_db before approving V2. Read-only inspection.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT current_database(), current_user;

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name = 'parcels' AND column_name IN
        ('id', 'sender_id', 'created_by', 'merchant_id', 'assigned_courier_id'))
    OR (table_name = 'parcel_status_history' AND column_name IN
        ('id', 'parcel_id', 'changed_by')))
ORDER BY table_name, column_name;

SELECT 'parcels' AS table_name, count(*) AS row_count FROM public.parcels
UNION ALL
SELECT 'parcel_status_history', count(*) FROM public.parcel_status_history;

SELECT installed_rank, version, description, checksum, success
FROM public.flyway_schema_history ORDER BY installed_rank;
COMMIT;

-- Missing tables/Flyway history require investigation, not automatic baselining.
-- If V2 is already applied, preserve its original checksum and use a new version
-- for any further schema change. Do not repair checksums to bypass review.
-- Populated UUID references require an authoritative Identity mapping:
-- 1. Add temporary BIGINT columns.
-- 2. Backfill using the mapping, preserving the existing UUID columns.
-- 3. Reject unresolved references and verify row counts and constraints.
-- 4. Replace columns and restore constraints/indexes in a reviewed transaction.
-- Never invent IDs, erase rows, or use USING NULL on populated tables.
