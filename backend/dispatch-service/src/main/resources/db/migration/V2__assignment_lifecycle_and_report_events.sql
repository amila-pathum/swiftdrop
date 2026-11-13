ALTER TABLE dispatch_couriers
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE dispatch_assignments
    ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN tracking_number VARCHAR(40),
    ADD COLUMN cancelled_by VARCHAR(80),
    ADD COLUMN cancelled_at TIMESTAMPTZ;

WITH ranked_assignments AS (
    SELECT assignment_id, parcel_id,
           row_number() OVER (PARTITION BY parcel_id ORDER BY assigned_at DESC, assignment_id DESC) AS position
      FROM dispatch_assignments
)
UPDATE dispatch_assignments a
   SET state = CASE
       WHEN p.status IN ('DELIVERED', 'FAILED') THEN 'COMPLETED'
       WHEN p.status = 'CANCELLED' OR ranked.position <> 1 THEN 'CANCELLED'
       ELSE 'ACTIVE'
   END,
       tracking_number = p.tracking_number
  FROM ranked_assignments ranked
  JOIN dispatch_parcels p ON p.parcel_id = ranked.parcel_id
 WHERE a.assignment_id = ranked.assignment_id;

INSERT INTO dispatch_assignments (
    assignment_id, parcel_id, courier_id, tracking_number, assigned_by, assigned_at, state
)
SELECT gen_random_uuid(), p.parcel_id, p.assigned_courier_id, p.tracking_number,
       'projection-migration', p.updated_at,
       CASE
           WHEN p.status IN ('DELIVERED', 'FAILED') THEN 'COMPLETED'
           WHEN p.status = 'CANCELLED' THEN 'CANCELLED'
           ELSE 'ACTIVE'
       END
  FROM dispatch_parcels p
 WHERE p.assigned_courier_id IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM dispatch_assignments a WHERE a.parcel_id = p.parcel_id
   );

ALTER TABLE dispatch_assignments
    ALTER COLUMN tracking_number SET NOT NULL,
    ADD CONSTRAINT chk_dispatch_assignment_state
        CHECK (state IN ('ACTIVE', 'COMPLETED', 'CANCELLED'));

ALTER TABLE dispatch_assignments
    DROP CONSTRAINT dispatch_assignments_parcel_id_fkey,
    DROP CONSTRAINT dispatch_assignments_courier_id_fkey,
    DROP CONSTRAINT dispatch_assignments_previous_courier_id_fkey;

CREATE UNIQUE INDEX uq_dispatch_assignment_active_parcel
    ON dispatch_assignments(parcel_id)
    WHERE state = 'ACTIVE';

CREATE INDEX idx_dispatch_assignment_state_courier
    ON dispatch_assignments(state, courier_id);

CREATE TABLE dispatch_report_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    tracking_number VARCHAR(40) NOT NULL,
    parcel_id VARCHAR(80) NOT NULL,
    merchant_id VARCHAR(80),
    merchant_order_reference VARCHAR(150),
    customer_id VARCHAR(80) NOT NULL,
    courier_id VARCHAR(80),
    old_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL
);

CREATE INDEX idx_dispatch_report_events_occurred
    ON dispatch_report_events(occurred_at, new_status);

CREATE INDEX idx_dispatch_report_events_parcel_time
    ON dispatch_report_events(parcel_id, occurred_at DESC);

CREATE INDEX idx_dispatch_report_events_courier_status
    ON dispatch_report_events(courier_id, new_status, occurred_at);
