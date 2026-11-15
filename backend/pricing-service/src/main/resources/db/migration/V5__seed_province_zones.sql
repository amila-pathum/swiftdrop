-- Delivery zones are Sri Lanka's nine provinces, each covering its districts'
-- capital cities (25 in all). Admins can rename zones and add more cities on the
-- Delivery Zones page afterwards.
--
-- Earlier test zones are replaced. Rates refer to zones, so any published rate
-- versions (there are none on the shared RDS database) go with them; publish rates
-- for the provinces on the Pricing page. Parcels store city names, not zone ids, so
-- existing parcels are unaffected.

DELETE FROM route_rates;
DELETE FROM rate_versions;

-- The shared RDS database also has an out-of-repo zone_districts table referring to zones.
DO $$
BEGIN
    IF to_regclass('public.zone_districts') IS NOT NULL THEN
        DELETE FROM zone_districts;
    END IF;
END $$;

DELETE FROM zone_localities;
DELETE FROM zones;

CREATE TEMPORARY TABLE province_seed (
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    localities TEXT[]       NOT NULL
) ON COMMIT DROP;

INSERT INTO province_seed (name, code, localities) VALUES
    ('Western',       'WP',  ARRAY['Colombo', 'Gampaha', 'Kalutara']),
    ('Central',       'CP',  ARRAY['Kandy', 'Matale', 'Nuwara Eliya']),
    ('Southern',      'SP',  ARRAY['Galle', 'Matara', 'Hambantota']),
    ('Northern',      'NP',  ARRAY['Jaffna', 'Kilinochchi', 'Mannar', 'Mullaitivu', 'Vavuniya']),
    ('Eastern',       'EP',  ARRAY['Trincomalee', 'Batticaloa', 'Ampara']),
    ('North Western', 'NWP', ARRAY['Kurunegala', 'Puttalam']),
    ('North Central', 'NCP', ARRAY['Anuradhapura', 'Polonnaruwa']),
    ('Uva',           'UP',  ARRAY['Badulla', 'Monaragala']),
    ('Sabaragamuwa',  'SGP', ARRAY['Ratnapura', 'Kegalle']);

INSERT INTO zones (id, name, code, active, created_at, updated_at)
SELECT gen_random_uuid(), name, code, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM province_seed;

INSERT INTO zone_localities (zone_id, position, locality)
SELECT z.id, l.ordinality - 1, l.locality
FROM province_seed s
JOIN zones z ON z.code = s.code
CROSS JOIN LATERAL unnest(s.localities) WITH ORDINALITY AS l(locality, ordinality);
