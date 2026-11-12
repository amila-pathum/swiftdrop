package com.swiftdrop.identity_service.migration;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the application with the real {@code rds} profile against a
 * throwaway PostgreSQL standing in for the service's own RDS database
 * (identity_db, owned by identity_user). The connection itself comes from the
 * container ({@code @ServiceConnection}), so this never contacts AWS; the DB_*
 * values below are what run-service.sh would map for this service, with a
 * host that cannot resolve. The container's URL replaces the profile's,
 * including its sslmode, so the profile file itself is checked separately.
 */
@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "DB_HOST=rds-host.invalid",
    "DB_NAME=" + RdsProfileStartupTest.DATABASE,
    "DB_USERNAME=" + RdsProfileStartupTest.USERNAME,
    "DB_PASSWORD=unused-with-service-connection"
})
@ActiveProfiles("rds")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RdsProfileStartupTest {

    static final String DATABASE = "identity_db";
    static final String USERNAME = "identity_user";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
        .withDatabaseName(DATABASE)
        .withUsername(USERNAME);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Environment environment;

    // application.properties baselines local pre-Flyway databases; the rds
    // profile must not, so every migration is applied as SQL.
    @Test
    void migrationsRunInThePublicSchemaWithoutBaselining() {
        assertThat(jdbc.queryForList("""
            SELECT schema_name FROM information_schema.schemata
            WHERE schema_name NOT LIKE 'pg\\_%' AND schema_name <> 'information_schema'
            ORDER BY schema_name
            """, String.class)).containsExactly("public");
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo("public");
        assertThat(jdbc.queryForList("""
            SELECT version || ':' || type FROM public.flyway_schema_history
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class)).containsExactly("1:SQL", "2:SQL");

        assertThat(jdbc.queryForList("""
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' ORDER BY table_name
            """, String.class))
            .containsExactly("flyway_schema_history", "oauth_login_codes", "user_identities", "users");
    }

    @Test
    void applicationReadsAndWritesThePublicSchema() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Rds Customer","email":"rds.customer@example.com","password":"password123"}
                    """))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"rds.customer@example.com","password":"password123"}
                    """))
            .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM public.users WHERE email = 'rds.customer@example.com'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void flywayAndHibernateAreConfiguredForThePublicSchema() {
        assertThat(flyway.getConfiguration().getDefaultSchema()).isEqualTo("public");
        assertThat(flyway.getConfiguration().getSchemas()).containsExactly("public");
        assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(entityManagerFactory.getProperties())
            .containsEntry("hibernate.default_schema", "public")
            .containsEntry("hibernate.hbm2ddl.auto", "validate");
    }

    // A database whose public schema already holds tables but no Flyway
    // history must stop the migration instead of being baselined as version 1
    // with V1__baseline.sql silently skipped.
    @Test
    void nonEmptyDatabaseWithoutFlywayHistoryIsRejected() {
        jdbc.execute("CREATE DATABASE baseline_guard");
        DataSource guard = new DriverManagerDataSource(
            "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/baseline_guard",
            postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(guard).execute("CREATE TABLE users (id bigint PRIMARY KEY)");

        Flyway guarded = Flyway.configure().configuration(flyway.getConfiguration()).dataSource(guard).load();
        assertThatThrownBy(guarded::migrate)
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("non-empty schema");
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

    // What the service would use on RDS: the container replaces only the connection.
    @Test
    void profileResolvesToTheServiceDatabaseAndUser() {
        assertThat(environment.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://rds-host.invalid:5432/" + DATABASE + "?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo(USERNAME);
    }

    // The container's URL replaces the profile's, so check the profile file itself.
    @Test
    void profileUsesEncryptedConnectionsAndThePublicSchema() throws Exception {
        Properties rds = new Properties();
        try (var in = new ClassPathResource("application-rds.properties").getInputStream()) {
            rds.load(in);
        }
        assertThat(rds.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}?sslmode=require");
        assertThat(rds.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(rds.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(rds.stringPropertyNames()).noneMatch(key -> key.contains("sslrootcert"));
        assertThat(rds.getProperty("spring.flyway.default-schema")).isEqualTo("public");
        assertThat(rds.getProperty("spring.flyway.schemas")).isEqualTo("public");
        assertThat(rds.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo("public");
        assertThat(rds.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(rds.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
    }
}
