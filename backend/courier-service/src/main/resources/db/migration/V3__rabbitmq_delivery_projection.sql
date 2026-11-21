ALTER TABLE courier_profiles ADD COLUMN dispatch_courier_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE courier_profiles ADD CONSTRAINT uq_courier_profile_dispatch_id UNIQUE (dispatch_courier_id);

CREATE TABLE courier_delivery_inbox (
    event_id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE courier_delivery_parcels (
    parcel_id UUID PRIMARY KEY,
    tracking_number VARCHAR(40) NOT NULL,
    status VARCHAR(30), status_at TIMESTAMPTZ,
    pickup_address TEXT, delivery_address TEXT,
    receiver_name VARCHAR(150), receiver_phone VARCHAR(30),
    weight NUMERIC(10,3), size VARCHAR(30),
    delivery_date DATE, snapshot_at TIMESTAMPTZ
);

-- projection_id identifies Courier's read model, not necessarily the real
-- Dispatch assignment. Never use this projection as authority to mutate Parcel.
CREATE TABLE courier_delivery_assignments (
    parcel_id UUID PRIMARY KEY REFERENCES courier_delivery_parcels(parcel_id),
    projection_id UUID NOT NULL,
    dispatch_assignment_id UUID,
    dispatch_courier_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    delivery_date DATE,
    attempt_no INTEGER NOT NULL DEFAULT 1 CHECK (attempt_no > 0),
    assignment_version BIGINT NOT NULL DEFAULT 0 CHECK (assignment_version >= 0)
);
CREATE INDEX idx_courier_delivery_owner ON courier_delivery_assignments(dispatch_courier_id, assigned_at DESC);
