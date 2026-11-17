-- All tables/columns belong to Courier. No other service database is queried.
ALTER TABLE courier_delivery_parcels ADD COLUMN merchant_user_id BIGINT CHECK (merchant_user_id > 0);
CREATE INDEX idx_courier_parcel_merchant ON courier_delivery_parcels(merchant_user_id, status, parcel_id);

ALTER TABLE courier_delivery_assignments
    ADD COLUMN managed_locally BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE courier_assignment_requests (
    submission_id UUID PRIMARY KEY,
    merchant_user_id BIGINT NOT NULL CHECK (merchant_user_id > 0),
    parcel_id UUID NOT NULL REFERENCES courier_delivery_parcels(parcel_id),
    fingerprint VARCHAR(64) NOT NULL,
    response TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE courier_event_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_key VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(500)
);
CREATE INDEX idx_courier_outbox_pending ON courier_event_outbox(next_attempt_at,occurred_at)
    WHERE published_at IS NULL;
