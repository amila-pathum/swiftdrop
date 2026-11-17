-- Dispatch no longer keeps copies of Parcel or Courier data. Parcels come from
-- parcel-service and couriers from courier-service, through their APIs; parcel
-- progress comes from the parcel status events stored in dispatch_report_events.
DROP TABLE dispatch_status_history;
DROP TABLE dispatch_parcels;
DROP TABLE dispatch_couriers;

-- Existing assignment rows refer to the removed demo couriers (UUIDs), so they
-- cannot be converted to courier-service profile ids.
DELETE FROM dispatch_assignments;

-- courier_profile_id: courier-service CourierProfile.id, exposed as "courierId".
-- courier_user_id:    the courier's identity user id; parcel-service stores it as
--                     the parcel's assigned courier and sends it as "courierId"
--                     in parcel status events.
-- version:            increases only when dispatch cancels the assignment
--                     (cancel or reassign), never when a status event completes it.
ALTER TABLE dispatch_assignments
    DROP COLUMN courier_id,
    DROP COLUMN previous_courier_id,
    ADD COLUMN courier_profile_id BIGINT NOT NULL,
    ADD COLUMN courier_user_id BIGINT NOT NULL,
    ADD COLUMN courier_name VARCHAR(100) NOT NULL,
    ADD COLUMN previous_courier_profile_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_dispatch_assignment_state_profile
    ON dispatch_assignments(state, courier_profile_id);
CREATE INDEX idx_dispatch_assignment_profile_time
    ON dispatch_assignments(courier_profile_id, assigned_at);
CREATE INDEX idx_dispatch_assignment_user
    ON dispatch_assignments(courier_user_id);
