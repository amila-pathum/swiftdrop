package com.swiftdrop.identity_service.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real migrations against PostgreSQL 18 (the version used in
 * development): once on an empty database and once on a copy of the schema
 * Hibernate created before Flyway was introduced.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    private static final String IMAGE = "postgres:18";

    @Container
    static final PostgreSQLContainer fresh = new PostgreSQLContainer(IMAGE);

    @Container
    static final PostgreSQLContainer existing = new PostgreSQLContainer(IMAGE)
        .withInitScript("db/pre-flyway-snapshot.sql");

    private static MigrateResult freshResult;
    private static MigrateResult existingResult;

    @BeforeAll
    static void migrate() {
        freshResult = flyway(fresh).migrate();
        existingResult = flyway(existing).migrate();
    }

    // Mirrors the spring.flyway.* settings in application.properties.
    private static Flyway flyway(PostgreSQLContainer container) {
        return Flyway.configure()
            .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load();
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer container) {
        return new JdbcTemplate(new DriverManagerDataSource(
            container.getJdbcUrl(), container.getUsername(), container.getPassword()));
    }

    private static List<String> history(PostgreSQLContainer container) {
        return jdbc(container).queryForList("""
            SELECT version || ':' || type || ':' || success
            FROM flyway_schema_history ORDER BY installed_rank
            """, String.class);
    }

    @Test
    void emptyDatabaseRunsEveryMigration() {
        assertThat(freshResult.success).isTrue();
        assertThat(history(fresh)).containsExactly("1:SQL:true", "2:SQL:true", "3:SQL:true");
    }

    @Test
    void existingDatabaseIsBaselinedAndOnlyRunsNewMigrations() {
        assertThat(existingResult.success).isTrue();
        assertThat(history(existing)).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true");
    }

    @Test
    void existingRowsArePreserved() {
        List<Map<String, Object>> rows = jdbc(existing).queryForList(
            "SELECT email, password_hash, role, status FROM users ORDER BY id");

        assertThat(rows).containsExactly(
            Map.of("email", "existing.customer@example.com", "password_hash", "pre-existing-hash-1",
                "role", "CUSTOMER", "status", "ACTIVE"),
            Map.of("email", "existing.merchant@example.com", "password_hash", "pre-existing-hash-2",
                "role", "MERCHANT", "status", "SUSPENDED"));
    }

    @Test
    void newUsersStillReceiveIdsAfterExistingRows() {
        Long id = jdbc(existing).queryForObject("""
            INSERT INTO users (created_at, email, name, password_hash, role, status, updated_at)
            VALUES (now(), 'after.migration@example.com', 'After', NULL, 'CUSTOMER', 'ACTIVE', now())
            RETURNING id
            """, Long.class);

        assertThat(id).isEqualTo(3L);
    }

    @Test
    void passwordHashBecomesOptional() {
        for (PostgreSQLContainer container : List.of(fresh, existing)) {
            assertThat(jdbc(container).queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'users' AND column_name = 'password_hash'
                """, String.class)).isEqualTo("YES");
        }
    }

    @Test
    void freshAndExistingDatabasesEndWithIdenticalSchemas() throws Exception {
        assertThat(schemaDump(existing)).isEqualTo(schemaDump(fresh));
    }

    @Test
    void migratingAgainChangesNothing() {
        assertThat(flyway(fresh).migrate().migrationsExecuted).isZero();
        assertThat(flyway(existing).migrate().migrationsExecuted).isZero();
    }

    // Structure only, without comments, session settings or Flyway's own table.
    private static String schemaDump(PostgreSQLContainer container) throws Exception {
        var result = container.execInContainer("pg_dump",
            "-U", container.getUsername(), "-d", container.getDatabaseName(),
            "--schema-only", "--no-owner", "--no-privileges",
            "--exclude-table=flyway_schema_history");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return Arrays.stream(result.getStdout().split("\n"))
            .filter(line -> !line.isBlank())
            .filter(line -> !line.startsWith("--") && !line.startsWith("SET ")
                && !line.startsWith("SELECT pg_catalog") && !line.startsWith("\\restrict")
                && !line.startsWith("\\unrestrict"))
            .collect(Collectors.joining("\n"));
    }
}
