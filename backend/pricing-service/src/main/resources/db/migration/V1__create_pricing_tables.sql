
-- Create delivery zones
CREATE TABLE zones (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL UNIQUE,
    district VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create pricing rules
CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY,
    zone_id UUID NOT NULL REFERENCES zones(id),
    base_fee NUMERIC(12,2) NOT NULL CHECK (base_fee >= 0),
    price_per_kg NUMERIC(12,2) NOT NULL CHECK (price_per_kg >= 0),
    express_multiplier NUMERIC(5,2) NOT NULL CHECK (express_multiplier >= 1),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pricing_rules_zone ON pricing_rules(zone_id);
