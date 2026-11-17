package com.swiftdrop.pricing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4 converts existing data: each zone's city becomes its first locality, zones get a
 * code, and localities from the shared RDS database's out-of-repo zone_districts table
 * are carried over without giving any locality two zones. V5 then replaces all zones
 * with the nine provinces.
 */
@Testcontainers(disabledWithoutDocker = true)
class ZoneMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Test void convertsZonesLikeTheSharedRdsDatabase() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("3").load().migrate();

        jdbc.execute("""
            INSERT INTO zones (id, name, city, district) VALUES
              ('00000000-0000-0000-0000-00000000000a', 'Western Metro', 'Colombo', 'Colombo'),
              ('00000000-0000-0000-0000-00000000000b', 'Central Hills', 'Kandy', 'Kandy');
            INSERT INTO pricing_rules (id, zone_id, base_fee, price_per_kg, express_multiplier)
              VALUES ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-00000000000a', 200, 150, 1.5);
            -- What the out-of-repo V2 left on RDS.
            ALTER TABLE zones ADD COLUMN code VARCHAR(20);
            UPDATE zones SET code = 'wp' WHERE name = 'Western Metro';
            CREATE TABLE zone_districts (zone_id UUID NOT NULL REFERENCES zones(id), district VARCHAR(100) NOT NULL);
            INSERT INTO zone_districts VALUES
              ('00000000-0000-0000-0000-00000000000a', 'Gampaha'),
              ('00000000-0000-0000-0000-00000000000a', 'Dehiwala'),
              ('00000000-0000-0000-0000-00000000000b', 'kandy'),
              ('00000000-0000-0000-0000-00000000000b', 'Gampaha'),
              ('00000000-0000-0000-0000-00000000000b', 'Matale');
            """);

        Flyway.configure().dataSource(dataSource).target("4").load().migrate();

        assertThat(jdbc.queryForList("SELECT name || ':' || code FROM zones ORDER BY name", String.class))
            .containsExactly("Central Hills:Z000000", "Western Metro:WP");
        // City first, then carried-over districts in name order; duplicates dropped.
        assertThat(jdbc.queryForList("""
            SELECT z.code || ':' || l.position || ':' || l.locality FROM zone_localities l
            JOIN zones z ON z.id = l.zone_id ORDER BY z.code, l.position
            """, String.class)).containsExactly(
                "WP:0:Colombo", "WP:1:Dehiwala", "WP:2:Gampaha",
                "Z000000:0:Kandy", "Z000000:1:Matale");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'zones' AND column_name IN ('city', 'district')",
            Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.pricing_rules') IS NULL", Boolean.class)).isTrue();

        // V5 then replaces those zones with the nine provinces and their district capitals.
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertThat(jdbc.queryForList("""
            SELECT z.code || ':' || z.name || ':' || string_agg(l.locality, ',' ORDER BY l.position)
            FROM zones z JOIN zone_localities l ON l.zone_id = z.id
            GROUP BY z.code, z.name ORDER BY z.code
            """, String.class)).containsExactly(
                "CP:Central:Kandy,Matale,Nuwara Eliya",
                "EP:Eastern:Trincomalee,Batticaloa,Ampara",
                "NCP:North Central:Anuradhapura,Polonnaruwa",
                "NP:Northern:Jaffna,Kilinochchi,Mannar,Mullaitivu,Vavuniya",
                "NWP:North Western:Kurunegala,Puttalam",
                "SGP:Sabaragamuwa:Ratnapura,Kegalle",
                "SP:Southern:Galle,Matara,Hambantota",
                "UP:Uva:Badulla,Monaragala",
                "WP:Western:Colombo,Gampaha,Kalutara");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM zone_localities", Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT bool_and(active) FROM zones", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM zone_districts", Integer.class)).isZero();

        // V6 publishes v1 with a rate for all 45 province pairs, LKR 350 to 2,000.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_versions WHERE version = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM route_rates", Integer.class)).isEqualTo(45);
        assertThat(jdbc.queryForObject("SELECT min(base_fee) || '-' || max(base_fee) FROM route_rates", String.class))
            .isEqualTo("350.00-2000.00");
        assertThat(jdbc.queryForObject(
            "SELECT bool_and(base_weight_kg = 1 AND express_multiplier = 1.5) FROM route_rates", Boolean.class)).isTrue();
    }

    // The seed never replaces rates an admin already published.
    @Test void rateSeedSkipsDatabasesThatAlreadyHaveRates() {
        var other = new PostgreSQLContainer("postgres:18");
        other.start();
        try {
            var dataSource = new DriverManagerDataSource(other.getJdbcUrl(), other.getUsername(), other.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).target("5").load().migrate();
            jdbc.execute("""
                INSERT INTO rate_versions (id, version, small_surcharge, medium_surcharge, large_surcharge, published_by, published_at)
                VALUES (gen_random_uuid(), 7, 1, 2, 3, 'admin', now())
                """);
            Flyway.configure().dataSource(dataSource).load().migrate();
            assertThat(jdbc.queryForList("SELECT version FROM rate_versions", Integer.class)).containsExactly(7);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM route_rates", Integer.class)).isZero();
        } finally {
            other.stop();
        }
    }
}
