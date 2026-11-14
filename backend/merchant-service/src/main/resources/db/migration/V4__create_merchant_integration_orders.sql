-- Member 9 order reservations; no customer contact data is stored here.
CREATE TABLE merchant_integration_orders (
    merchant_id VARCHAR(150) NOT NULL,
    merchant_order_reference VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    tracking_number VARCHAR(100),
    parcel_status VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id, merchant_order_reference),
    UNIQUE (merchant_id, tracking_number)
);
