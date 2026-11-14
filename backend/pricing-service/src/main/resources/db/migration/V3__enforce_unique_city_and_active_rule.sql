-- Match the case-insensitive application lookup, including concurrent requests.
CREATE UNIQUE INDEX uq_zones_city_normalized ON zones (lower(btrim(city)));
-- Historical disabled rules are retained; a zone has at most one current rule.
CREATE UNIQUE INDEX uq_pricing_rules_active_zone ON pricing_rules (zone_id) WHERE active;
