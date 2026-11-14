CREATE TABLE parcel_status_idempotency (
    idempotency_key UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    requested_status VARCHAR(30) NOT NULL,
    requested_note VARCHAR(1000),
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    changed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_status_idempotency_result CHECK (
        (previous_status IS NULL AND new_status IS NULL AND changed_at IS NULL)
        OR
        (previous_status IS NOT NULL AND new_status IS NOT NULL AND changed_at IS NOT NULL)
    )
);

CREATE INDEX idx_parcel_status_idempotency_parcel
    ON parcel_status_idempotency(parcel_id, created_at);
