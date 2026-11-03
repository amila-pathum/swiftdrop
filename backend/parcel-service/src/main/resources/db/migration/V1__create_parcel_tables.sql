-- Initial Parcel Core schema. Apply through Flyway, never manually to an existing DB.
CREATE TABLE parcels (
    id UUID PRIMARY KEY,
    tracking_number VARCHAR(40) NOT NULL UNIQUE,
    sender_id UUID NOT NULL,
    receiver_name VARCHAR(150) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    pickup_address TEXT NOT NULL,
    delivery_address TEXT NOT NULL,
    weight NUMERIC(10,3) NOT NULL CHECK (weight > 0),
    size VARCHAR(30) NOT NULL,
    parcel_type VARCHAR(50) NOT NULL,
    charge NUMERIC(12,2) NOT NULL CHECK (charge >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    created_by UUID NOT NULL,
    merchant_id UUID,
    merchant_order_reference VARCHAR(150),
    assigned_courier_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_parcel_status CHECK (status IN
        ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED'))
);

CREATE INDEX idx_parcels_sender ON parcels(sender_id);
CREATE INDEX idx_parcels_merchant ON parcels(merchant_id);
CREATE INDEX idx_parcels_status ON parcels(status);

CREATE TABLE parcel_status_history (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by UUID NOT NULL,
    note TEXT,
    CONSTRAINT chk_history_previous_status CHECK (previous_status IS NULL OR previous_status IN
        ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED')),
    CONSTRAINT chk_history_new_status CHECK (new_status IN
        ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED'))
);

CREATE INDEX idx_parcel_status_history_parcel_time
    ON parcel_status_history(parcel_id, changed_at);
