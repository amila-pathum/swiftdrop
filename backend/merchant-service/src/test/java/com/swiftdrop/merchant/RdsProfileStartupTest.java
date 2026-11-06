package com.swiftdrop.merchant;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the service with the real {@code rds} profile against a throwaway
 * PostgreSQL whose public schema already holds other services' objects, as the
 * shared RDS database may. The connection itself comes from the container
 * ({@code @ServiceConnection}), so this never contacts AWS; the DB_* values
 * below only satisfy the profile's placeholders. The container's URL replaces
 * the profile's, including its sslmode, which is checked separately.
 */
@SpringBootTest(properties = {
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "DB_HOST=unused-with-service-connection",
    "DB_NAME=unused-with-service-connection",
    "DB_USERNAME=unused-with-service-connection",
    "DB_PASSWORD=unused-with-service-connection"
})
@ActiveProfiles("rds")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RdsProfileStartupTest {

    private static final String SCHEMA = "merchant";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
        .withInitScript("db/shared-database-public-schema.sql");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void startsAndReportsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void flywayCreatesOnlyTheServiceSchema() {
        assertThat(jdbc.queryForList("""
            SELECT schema_name FROM information_schema.schemata
            WHERE schema_name NOT LIKE 'pg\\_%' AND schema_name <> 'information_schema'
            ORDER BY schema_name
            """, String.class)).containsExactly(SCHEMA, "public");
        assertThat(jdbc.queryForList(
            "SELECT version || ':' || type FROM " + SCHEMA + ".flyway_schema_history"
                + " WHERE version IS NOT NULL ORDER BY installed_rank",
            String.class)).containsExactly();
        assertThat(jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name",
            String.class, SCHEMA)).containsExactly("flyway_schema_history");
    }

    @Test
    void publicSchemaIsLeftUntouched() {
        assertThat(jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
            String.class)).containsExactly("flyway_schema_history");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM public.flyway_schema_history", Integer.class)).isZero();
    }

    @Test
    void poolSettingsComeFromTheProfile() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(2);
        assertThat(hikari.getMinimumIdle()).isZero();
        assertThat(hikari.getIdleTimeout()).isEqualTo(120_000);
        assertThat(hikari.getMaxLifetime()).isEqualTo(1_500_000);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(15_000);
    }

    // The container's URL replaces the profile's, so check the profile file itself.
    @Test
    void profileUsesEncryptedConnectionsAndTheServiceSchema() throws Exception {
        Properties rds = new Properties();
        try (var in = new ClassPathResource("application-rds.properties").getInputStream()) {
            rds.load(in);
        }
        assertThat(rds.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}?sslmode=require");
        assertThat(rds.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(rds.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(rds.getProperty("spring.flyway.default-schema")).isEqualTo(SCHEMA);
        assertThat(rds.getProperty("spring.flyway.schemas")).isEqualTo(SCHEMA);
        assertThat(rds.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo(SCHEMA);
        assertThat(rds.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(rds.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
    }
}
