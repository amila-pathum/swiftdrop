package com.swiftdrop.courier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the whole service against a throwaway PostgreSQL (needs Docker). */
@SpringBootTest(properties = {
    "COURIER_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CourierServiceApplicationTests {
    private static final byte[] KEY = IntStream.range(0, 32).collect(
        () -> new byte[32], (bytes, index) -> bytes[index] = (byte) index, (left, right) -> { });

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mockMvc;

    @Test
    void contextLoadsAndReportsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void dispatcherCanReadCourierProfiles() throws Exception {
        mockMvc.perform(get("/api/couriers").header("Authorization", token("DISPATCHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
            .andExpect(jsonPath("$[0].courierId").exists())
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].available").value(true));
        mockMvc.perform(get("/api/couriers/{id}", "10000000-0000-4000-8000-000000000001")
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Demo Courier A"));
    }

    @Test
    void courierProfileReadIsRestrictedToDispatchAndAdmin() throws Exception {
        mockMvc.perform(get("/api/couriers").header("Authorization", token("COURIER")))
            .andExpect(status().isForbidden());
    }

    private static String token(String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject("42").claim("role", role)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900)))
            .signWith(Keys.hmacShaKeyFor(KEY)).compact();
    }
}
