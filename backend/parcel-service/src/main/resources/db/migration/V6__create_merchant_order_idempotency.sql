-- The key is the authenticated Identity merchant user ID, not a Merchant profile ID.
-- Reservation, parcel, history and outbox commit or roll back together.
CREATE TABLE merchant_order_idempotency (
    merchant_user_id BIGINT NOT NULL CHECK (merchant_user_id > 0),
    merchant_order_reference VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    parcel_id UUID REFERENCES parcels(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (merchant_user_id, merchant_order_reference),
    UNIQUE (parcel_id)
);
