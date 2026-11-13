package com.swiftdrop.dispatch;

import com.swiftdrop.dispatch.event.DispatchEventPublisher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the whole service against a throwaway PostgreSQL (needs Docker). */
@SpringBootTest(properties = {
    "DISPATCH_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DispatchServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DispatchEventPublisher eventPublisher;

    private static final String PARCEL_ONE = "20000000-0000-4000-8000-000000000001";
    private static final String PARCEL_PICKED_UP = "20000000-0000-4000-8000-000000000003";
    private static final String COURIER_ONE = "10000000-0000-4000-8000-000000000001";
    private static final String COURIER_TWO = "10000000-0000-4000-8000-000000000002";
    private static final byte[] KEY = IntStream.range(0, 32).collect(
        () -> new byte[32], (bytes, index) -> bytes[index] = (byte) index, (left, right) -> { });

    @BeforeEach
    void resetDemoAssignment() {
        UUID parcelId = UUID.fromString(PARCEL_ONE);
        jdbc.update("DELETE FROM dispatch_assignments WHERE parcel_id = ?", parcelId);
        jdbc.update("UPDATE dispatch_parcels SET status = 'CREATED', assigned_courier_id = NULL "
            + "WHERE parcel_id = ?", parcelId);
    }

    @Test
    void contextLoadsAndReportsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void dispatcherCanListUnassignedParcelsAndCourierWorkload() throws Exception {
        mockMvc.perform(get("/api/dispatch/parcels/unassigned")
                .header("Authorization", token("DISPATCHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content", hasSize(3)));
        mockMvc.perform(get("/api/dispatch/couriers")
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].activeParcelCount").exists());
    }

    @Test
    void assignmentIsAuditedAndCanBeReassignedBeforePickup() throws Exception {
        mockMvc.perform(post("/api/dispatch/parcels/{id}/assignment", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\":\"" + COURIER_ONE + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierId").value(COURIER_ONE))
            .andExpect(jsonPath("$.previousCourierId").doesNotExist());
        mockMvc.perform(post("/api/dispatch/parcels/{id}/assignment", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\":\"" + COURIER_TWO + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previousCourierId").value(COURIER_ONE));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM dispatch_assignments WHERE parcel_id = ?", Integer.class,
            UUID.fromString(PARCEL_ONE)))
            .isEqualTo(2);
    }

    @Test
    void assignmentRejectsUnknownCourierAndParcelAlreadyPickedUp() throws Exception {
        mockMvc.perform(post("/api/dispatch/parcels/{id}/assignment", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\":\"99999999-0000-4000-8000-000000000001\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/dispatch/parcels/{id}/assignment", PARCEL_PICKED_UP)
                .header("Authorization", token("DISPATCHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\":\"" + COURIER_TWO + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void reportEndpointsAggregateProjectionAndExportCsv() throws Exception {
        String period = "?from=" + java.time.LocalDate.now().minusDays(10)
            + "&to=" + java.time.LocalDate.now();
        mockMvc.perform(post("/api/dispatch/parcels/{id}/assignment", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\":\"" + COURIER_ONE + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/dispatch/reports/status" + period)
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(6)))
            .andExpect(jsonPath("$[0].status").value("CREATED"))
            .andExpect(jsonPath("$[0].count").value(8));
        mockMvc.perform(get("/api/dispatch/reports/volume" + period)
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].count").value(1));
        mockMvc.perform(get("/api/dispatch/reports/courier-performance" + period)
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].parcelsHandled").value(1))
            .andExpect(jsonPath("$[0].failed").value(1))
            .andExpect(jsonPath("$[1].delivered").value(1));
        mockMvc.perform(get("/api/dispatch/reports/failed-deliveries" + period)
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reason").value("Recipient unavailable"));
        mockMvc.perform(get("/api/dispatch/reports/revenue-by-route" + period)
                .header("Authorization", token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].totalFees").exists());
        mockMvc.perform(get("/api/dispatch/reports/status.csv" + period)
                .header("Authorization", token("DISPATCHER")))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void otherRolesCannotReadDispatchData() throws Exception {
        mockMvc.perform(get("/api/dispatch/parcels/unassigned")
                .header("Authorization", token("COURIER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Access is denied"));
    }

    private static String token(String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder()
            .subject("42")
            .claim("email", "dispatcher@example.com")
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900)))
            .signWith(Keys.hmacShaKeyFor(KEY))
            .compact();
    }
}
