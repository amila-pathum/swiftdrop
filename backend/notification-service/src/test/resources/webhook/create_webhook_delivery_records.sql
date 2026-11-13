-- Member 9 owns these tables. Coordinate migration numbers with Member 10.
-- Never place signing secrets, full addresses or phone numbers in the payload.
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    merchant_id VARCHAR(150) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    merchant_order_reference VARCHAR(150),
    status_sent VARCHAR(50) NOT NULL,
    payload BYTEA NOT NULL,
    state VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'RETRY_SCHEDULED', 'DELIVERED', 'FAILED', 'SKIPPED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_webhook_event_merchant UNIQUE (event_id, merchant_id)
);
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries(next_attempt_at)
    WHERE state IN ('PENDING', 'RETRY_SCHEDULED');
CREATE INDEX idx_webhook_deliveries_merchant ON webhook_deliveries(merchant_id, created_at);

CREATE TABLE webhook_delivery_attempts (
    id BIGSERIAL PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES webhook_deliveries(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    attempted_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    http_status INTEGER,
    failure_code VARCHAR(50),
    next_retry_at TIMESTAMPTZ,
    CONSTRAINT uq_webhook_attempt UNIQUE (delivery_id, attempt_number)
);
