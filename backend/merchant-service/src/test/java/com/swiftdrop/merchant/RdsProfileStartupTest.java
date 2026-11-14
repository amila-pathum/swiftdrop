package com.swiftdrop.merchant;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the RDS profile using a temporary PostgreSQL container.
 * Does not connect to the real AWS RDS database.
 */
@SpringBootTest(properties = {
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "DB_HOST=rds-host.invalid",
    "DB_NAME=" + RdsProfileStartupTest.DATABASE,
    "DB_USERNAME=" + RdsProfileStartupTest.USERNAME,
    "DB_PASSWORD=unused-with-service-connection"
})
@ActiveProfiles("rds")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RdsProfileStartupTest {

    static final String DATABASE = "merchant_db";
    static final String USERNAME = "merchant_user";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName(DATABASE)
                    .withUsername(USERNAME);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    Environment environment;

    @Test
    void startsAndReportsHealthy() throws Exception {

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Verify Flyway creates the three merchant migrations
     * in the public schema only.
     */
    @Test
    void flywayUsesOnlyThePublicSchema() {

        assertThat(jdbc.queryForList("""
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name NOT LIKE 'pg\\_%'
              AND schema_name <> 'information_schema'
            ORDER BY schema_name
            """, String.class))
                .containsExactly("public");

        assertThat(jdbc.queryForObject(
                "SELECT current_schema()",
                String.class))
                .isEqualTo("public");

        assertThat(jdbc.queryForList(
                "SELECT version || ':' || type " +
                "FROM public.flyway_schema_history " +
                "WHERE version IS NOT NULL " +
                "ORDER BY installed_rank",
                String.class))
                .containsExactly(
                        "1:SQL",
                        "2:SQL",
                        "3:SQL",
                        "4:SQL"
                );

        assertThat(jdbc.queryForList(
                "SELECT table_name " +
                "FROM information_schema.tables " +
                "WHERE table_schema = 'public' " +
                "ORDER BY table_name",
                String.class))
                .containsExactly(
                        "flyway_schema_history",
                        "merchant_api_keys",
                        "merchant_integration_orders",
                        "merchant_webhook_settings",
                        "merchants"
                );
    }

    @Test
    void flywayAndHibernateAreConfiguredForThePublicSchema() {

        assertThat(flyway.getConfiguration().getDefaultSchema())
                .isEqualTo("public");

        assertThat(flyway.getConfiguration().getSchemas())
                .containsExactly("public");

        assertThat(flyway.getConfiguration().isBaselineOnMigrate())
                .isFalse();

        assertThat(entityManagerFactory.getProperties())
                .containsEntry(
                        "hibernate.default_schema",
                        "public"
                )
                .containsEntry(
                        "hibernate.hbm2ddl.auto",
                        "validate"
                );
    }

    /**
     * Flyway must reject a non-empty database without
     * migration history.
     */
    @Test
    void nonEmptyDatabaseWithoutFlywayHistoryIsRejected() {

        jdbc.execute("CREATE DATABASE baseline_guard");

        DataSource guard = new DriverManagerDataSource(
                "jdbc:postgresql://" +
                postgres.getHost() + ":" +
                postgres.getMappedPort(5432) +
                "/baseline_guard",
                postgres.getUsername(),
                postgres.getPassword()
        );

        new JdbcTemplate(guard).execute(
                "CREATE TABLE created_outside_flyway " +
                "(id int PRIMARY KEY)"
        );

        Flyway guarded = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .dataSource(guard)
                .load();

        assertThatThrownBy(guarded::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");
    }

    @Test
    void poolSettingsComeFromTheProfile() {

        HikariDataSource hikari =
                (HikariDataSource) dataSource;

        assertThat(hikari.getMaximumPoolSize())
                .isEqualTo(2);

        assertThat(hikari.getMinimumIdle())
                .isZero();

        assertThat(hikari.getIdleTimeout())
                .isEqualTo(120_000);

        assertThat(hikari.getMaxLifetime())
                .isEqualTo(1_500_000);

        assertThat(hikari.getConnectionTimeout())
                .isEqualTo(15_000);
    }

    @Test
    void profileResolvesToTheServiceDatabaseAndUser() {

        assertThat(environment.getProperty(
                "spring.datasource.url"))
                .isEqualTo(
                    "jdbc:postgresql://rds-host.invalid:5432/" +
                    DATABASE + "?sslmode=require"
                );

        assertThat(environment.getProperty(
                "spring.datasource.username"))
                .isEqualTo(USERNAME);
    }

    @Test
    void profileUsesEncryptedConnectionsAndThePublicSchema()
            throws Exception {

        Properties rds = new Properties();

        try (var in = new ClassPathResource(
                "application-rds.properties").getInputStream()) {
            rds.load(in);
        }

        assertThat(rds.getProperty(
                "spring.datasource.url"))
                .isEqualTo(
                    "jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}?sslmode=require"
                );

        assertThat(rds.getProperty(
                "spring.datasource.username"))
                .isEqualTo("${DB_USERNAME}");

        assertThat(rds.getProperty(
                "spring.datasource.password"))
                .isEqualTo("${DB_PASSWORD}");

        assertThat(rds.stringPropertyNames())
                .noneMatch(key -> key.contains("sslrootcert"));

        assertThat(rds.getProperty(
                "spring.flyway.default-schema"))
                .isEqualTo("public");

        assertThat(rds.getProperty(
                "spring.flyway.schemas"))
                .isEqualTo("public");

        assertThat(rds.getProperty(
                "spring.jpa.properties.hibernate.default_schema"))
                .isEqualTo("public");

        assertThat(rds.getProperty(
                "spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");

        assertThat(rds.getProperty(
                "spring.flyway.baseline-on-migrate"))
                .isEqualTo("false");
    }
}
