-- Run in parcel_db with psql -v parcel_id=<Postman parcelId> -f verify-created-parcel.sql
-- Read-only. Expect one parcel and one initial null -> CREATED history row.
BEGIN TRANSACTION READ ONLY;
SELECT id, tracking_number, sender_id, created_by, charge, status, created_at
FROM parcels WHERE id = :'parcel_id'::uuid;

SELECT h.previous_status, h.new_status, h.changed_by, h.changed_at,
       h.changed_by = p.created_by AS actor_matches_creator
FROM parcel_status_history h JOIN parcels p ON p.id = h.parcel_id
WHERE p.id = :'parcel_id'::uuid ORDER BY h.changed_at;
COMMIT;
