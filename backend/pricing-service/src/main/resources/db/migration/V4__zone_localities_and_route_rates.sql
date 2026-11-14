-- Zones are identified by a short code and cover a list of localities (the cities
-- customers pick when booking). A locality belongs to exactly one zone.
--
-- The shared RDS database already has an out-of-repo V2 that added zones.code and
-- a zone_districts table, so this migration works whether or not that ran.

ALTER TABLE zones ADD COLUMN IF NOT EXISTS code VARCHAR(20);

-- Existing zones get a placeholder code an admin can rename.
UPDATE zones
SET code = 'Z' || upper(substr(replace(id::text, '-', ''), 1, 6))
WHERE code IS NULL OR btrim(code) = '';

UPDATE zones SET code = upper(btrim(code));

ALTER TABLE zones ALTER COLUMN code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_zones_code_normalized ON zones (upper(btrim(code)));

CREATE TABLE zone_localities (
    zone_id  UUID         NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    position INT          NOT NULL CHECK (position >= 0),
    locality VARCHAR(100) NOT NULL CHECK (btrim(locality) <> ''),
    PRIMARY KEY (zone_id, position)
);

CREATE UNIQUE INDEX uq_zone_localities_locality ON zone_localities (lower(btrim(locality)));

-- Each zone's old single city becomes its first locality.
INSERT INTO zone_localities (zone_id, position, locality)
SELECT id, 0, btrim(city) FROM zones;

-- Carry over localities from the out-of-repo zone_districts table when it exists,
-- skipping any already covered so every locality still has exactly one zone.
DO $$
BEGIN
    IF to_regclass('public.zone_districts') IS NOT NULL THEN
        INSERT INTO zone_localities (zone_id, position, locality)
        SELECT zone_id, row_number() OVER (PARTITION BY zone_id ORDER BY locality), locality
        FROM (
            SELECT DISTINCT ON (lower(btrim(d.district))) d.zone_id, btrim(d.district) AS locality
            FROM zone_districts d
            WHERE btrim(d.district) <> ''
              AND NOT EXISTS (
                  SELECT 1 FROM zone_localities l
                  WHERE lower(btrim(l.locality)) = lower(btrim(d.district)))
            ORDER BY lower(btrim(d.district)), d.zone_id
        ) carried;
    END IF;
END $$;

-- City and district are replaced by the locality list (drops V3's city index too).
ALTER TABLE zones DROP COLUMN city;
ALTER TABLE zones DROP COLUMN district;

-- Per-zone pricing rules are replaced by published rate versions with one rate per
-- pair of zones (the same in both directions).
DROP TABLE pricing_rules;

CREATE TABLE rate_versions (
    id               UUID          PRIMARY KEY,
    version          INT           NOT NULL UNIQUE CHECK (version > 0),
    small_surcharge  NUMERIC(12,2) NOT NULL CHECK (small_surcharge >= 0),
    medium_surcharge NUMERIC(12,2) NOT NULL CHECK (medium_surcharge >= 0),
    large_surcharge  NUMERIC(12,2) NOT NULL CHECK (large_surcharge >= 0),
    published_by     VARCHAR(100)  NOT NULL,
    published_at     TIMESTAMPTZ   NOT NULL
);

CREATE TABLE route_rates (
    id                 UUID          PRIMARY KEY,
    rate_version_id    UUID          NOT NULL REFERENCES rate_versions(id) ON DELETE CASCADE,
    zone_a_id          UUID          NOT NULL REFERENCES zones(id),
    zone_b_id          UUID          NOT NULL REFERENCES zones(id),
    base_weight_kg     NUMERIC(10,3) NOT NULL CHECK (base_weight_kg >= 0),
    base_fee           NUMERIC(12,2) NOT NULL CHECK (base_fee >= 0),
    additional_kg_fee  NUMERIC(12,2) NOT NULL CHECK (additional_kg_fee >= 0),
    express_multiplier NUMERIC(5,2)  NOT NULL CHECK (express_multiplier >= 1),
    -- Each pair is stored once, smaller zone id first.
    CHECK (zone_a_id <= zone_b_id),
    UNIQUE (rate_version_id, zone_a_id, zone_b_id)
);
