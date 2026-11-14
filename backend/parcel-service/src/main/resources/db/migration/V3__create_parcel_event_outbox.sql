CREATE TABLE parcel_event_outbox (
    event_id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    routing_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(1000)
);

CREATE INDEX idx_parcel_event_outbox_pending
    ON parcel_event_outbox(next_attempt_at, created_at)
    WHERE published_at IS NULL;
