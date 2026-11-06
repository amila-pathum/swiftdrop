package com.swiftdrop.identity_service.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the application with the real {@code rds} profile against a throwaway
 * PostgreSQL whose public schema already holds another service's objects, as
 * the shared RDS database may. The connection itself comes from the container
 * ({@code @ServiceConnection}), so this never contacts AWS; the DB_* values
 * below only satisfy the profile's placeholders. The container's URL replaces
 * the profile's, including its sslmode, which is checked separately.
 */
@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "DB_HOST=unused-with-service-connection",
    "DB_NAME=unused-with-service-connection",
    "DB_USERNAME=unused-with-service-connection",
    "DB_PASSWORD=unused-with-service-connection"
})
@ActiveProfiles("rds")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RdsProfileStartupTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
        .withInitScript("db/shared-database-public-schema.sql");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void migrationsRunInTheIdentitySchemaWithoutBaselining() {
        assertThat(jdbc.queryForList("""
            SELECT version || ':' || type FROM identity.flyway_schema_history
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class)).containsExactly("1:SQL", "2:SQL");

        assertThat(jdbc.queryForList("""
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'identity' ORDER BY table_name
            """, String.class))
            .containsExactly("flyway_schema_history", "oauth_login_codes", "user_identities", "users");
    }

    @Test
    void publicSchemaIsLeftUntouched() {
        assertThat(jdbc.queryForList("""
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' ORDER BY table_name
            """, String.class)).containsExactly("flyway_schema_history", "users");
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'users'
            """, Integer.class)).isEqualTo(1);
    }

    @Test
    void applicationReadsAndWritesTheIdentitySchema() throws Exception {
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
            "SELECT count(*) FROM identity.users WHERE email = 'rds.customer@example.com'",
            Integer.class)).isEqualTo(1);
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
    void profileRequiresEncryptedConnections() throws Exception {
        Properties rds = new Properties();
        try (var in = new ClassPathResource("application-rds.properties").getInputStream()) {
            rds.load(in);
        }
        assertThat(rds.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}?sslmode=require");
        assertThat(rds.stringPropertyNames()).noneMatch(key -> key.contains("sslrootcert"));
    }
}
