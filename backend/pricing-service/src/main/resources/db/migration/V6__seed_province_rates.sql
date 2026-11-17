-- Publishes rate version v1 for the nine province zones from V5, so quotes work
-- straight away. Admins change them on the Pricing page (each publish is a new version).
--
-- Skipped on any database that already has published rates, so it never replaces
-- rates an admin has set.
--
-- How the numbers were chosen: from the approximate road distance between the
-- province capitals (Colombo, Kandy, Galle, Jaffna, Trincomalee, Kurunegala,
-- Anuradhapura, Badulla, Ratnapura), or a typical distance within the province:
--   base fee (covers the first 1 kg) = 300 + 3.3 LKR per km, to the nearest 50, at most 2,000
--   each additional kg               =  60 + 0.2 LKR per km, to the nearest 10
-- That gives LKR 350 within Western up to LKR 2,000 for Southern <-> Northern, in line
-- with typical Sri Lankan courier rates. Express is x1.5 on every route; size
-- surcharges are Small 0, Medium 150, Large 300.

DO $$
DECLARE
    seeded UUID := gen_random_uuid();
BEGIN
    IF EXISTS (SELECT 1 FROM rate_versions) THEN
        RETURN;
    END IF;

    INSERT INTO rate_versions (id, version, small_surcharge, medium_surcharge, large_surcharge,
                               published_by, published_at)
    VALUES (seeded, 1, 0, 150, 300, 'initial-rates', CURRENT_TIMESTAMP);

    INSERT INTO route_rates (id, rate_version_id, zone_a_id, zone_b_id, base_weight_kg,
                             base_fee, additional_kg_fee, express_multiplier)
    SELECT gen_random_uuid(), seeded, LEAST(a.id, b.id), GREATEST(a.id, b.id), 1,
           r.base_fee, r.additional_kg_fee, 1.5
    FROM (VALUES
        -- (zone, zone, approx. km, base fee, each additional kg)
        ('WP',  'WP',   15,  350,  60),
        ('WP',  'CP',  115,  700,  80),
        ('WP',  'SP',  120,  700,  80),
        ('WP',  'NP',  395, 1600, 140),
        ('WP',  'EP',  260, 1150, 110),
        ('WP',  'NWP',  95,  600,  80),
        ('WP',  'NCP', 205, 1000, 100),
        ('WP',  'UP',  230, 1050, 110),
        ('WP',  'SGP', 100,  650,  80),
        ('CP',  'CP',   40,  450,  70),
        ('CP',  'SP',  220, 1050, 100),
        ('CP',  'NP',  315, 1350, 120),
        ('CP',  'EP',  180,  900, 100),
        ('CP',  'NWP',  42,  450,  70),
        ('CP',  'NCP', 140,  750,  90),
        ('CP',  'UP',  125,  700,  90),
        ('CP',  'SGP', 135,  750,  90),
        ('SP',  'SP',   45,  450,  70),
        ('SP',  'NP',  510, 2000, 160),
        ('SP',  'EP',  380, 1550, 140),
        ('SP',  'NWP', 215, 1000, 100),
        ('SP',  'NCP', 320, 1350, 120),
        ('SP',  'UP',  240, 1100, 110),
        ('SP',  'SGP', 140,  750,  90),
        ('NP',  'NP',   60,  500,  70),
        ('NP',  'EP',  240, 1100, 110),
        ('NP',  'NWP', 300, 1300, 120),
        ('NP',  'NCP', 195,  950, 100),
        ('NP',  'UP',  420, 1700, 140),
        ('NP',  'SGP', 420, 1700, 140),
        ('EP',  'EP',   80,  550,  80),
        ('EP',  'NWP', 165,  850,  90),
        ('EP',  'NCP', 105,  650,  80),
        ('EP',  'UP',  260, 1150, 110),
        ('EP',  'SGP', 300, 1300, 120),
        ('NWP', 'NWP',  45,  450,  70),
        ('NWP', 'NCP', 115,  700,  80),
        ('NWP', 'UP',  165,  850,  90),
        ('NWP', 'SGP', 135,  750,  90),
        ('NCP', 'NCP',  50,  450,  70),
        ('NCP', 'UP',  230, 1050, 110),
        ('NCP', 'SGP', 250, 1150, 110),
        ('UP',  'UP',   45,  450,  70),
        ('UP',  'SGP', 165,  850,  90),
        ('SGP', 'SGP',  40,  450,  70)
    ) AS r(code_a, code_b, km, base_fee, additional_kg_fee)
    JOIN zones a ON a.code = r.code_a
    JOIN zones b ON b.code = r.code_b;
END $$;
