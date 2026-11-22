ALTER TABLE courier_delivery_parcels
    ADD COLUMN pickup_contact_name VARCHAR(150),
    ADD COLUMN pickup_contact_phone VARCHAR(30),
    ADD COLUMN contents_description VARCHAR(500),
    ADD COLUMN length_cm NUMERIC(10,3) CHECK (length_cm > 0),
    ADD COLUMN width_cm NUMERIC(10,3) CHECK (width_cm > 0),
    ADD COLUMN height_cm NUMERIC(10,3) CHECK (height_cm > 0);

CREATE TABLE courier_delivery_history (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES courier_delivery_parcels(parcel_id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED')),
    recorded_at TIMESTAMPTZ NOT NULL,
    location_description VARCHAR(500),
    attempt_no INTEGER CHECK (attempt_no > 0),
    actor_role VARCHAR(30)
);
CREATE INDEX idx_courier_history_parcel_time ON courier_delivery_history(parcel_id,recorded_at,id);
