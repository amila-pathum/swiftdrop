package com.swiftdrop.dispatch;

import com.swiftdrop.dispatch.client.DispatchDataClient;
import com.swiftdrop.dispatch.client.DispatchDataClient.CourierData;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.event.DispatchEventPublisher;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.service.DispatchService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole service against a throwaway PostgreSQL (needs Docker).
 * parcel-service and courier-service are replaced by a mocked DispatchDataClient.
 */
@SpringBootTest(properties = {
    "DISPATCH_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DispatchServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DispatchService dispatch;
    @MockitoBean DispatchEventPublisher eventPublisher;
    @MockitoBean DispatchDataClient dataClient;

    private static final UUID PARCEL_ONE = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID PARCEL_TWO = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final CourierData AMAL = new CourierData(7L, 70L, "Amal", "AVAILABLE", true);
    private static final CourierData BIMAL = new CourierData(9L, 90L, "Bimal", "AVAILABLE", true);
    private static final CourierData BUSY = new CourierData(11L, 110L, "Chamal", "BUSY", true);
    private static final byte[] KEY = IntStream.range(0, 32).collect(
        () -> new byte[32], (bytes, index) -> bytes[index] = (byte) index, (left, right) -> { });

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM dispatch_assignments");
        jdbc.update("DELETE FROM dispatch_report_events");
        givenParcel(PARCEL_ONE, "SD-0001", "CREATED");
        givenParcel(PARCEL_TWO, "SD-0002", "CREATED");
        when(dataClient.createdParcels()).thenReturn(List.of(
            parcel(PARCEL_ONE, "SD-0001", "CREATED"), parcel(PARCEL_TWO, "SD-0002", "CREATED")));
        when(dataClient.couriers()).thenReturn(List.of(AMAL, BIMAL, BUSY));
        when(dataClient.findCourier(anyLong())).thenAnswer(call -> List.of(AMAL, BIMAL, BUSY).stream()
            .filter(courier -> courier.id().equals(call.getArgument(0))).findFirst());
    }

    @Test
    void contextLoadsAndReportsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // ------------------------------------------------------------ dispatcher board

    @Test
    void assignedParcelLeavesTheWaitingListAndCountsTowardsTheCourierWorkload() throws Exception {
        mockMvc.perform(get("/api/dispatch/parcels/unassigned").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));

        assign(PARCEL_ONE, 7)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.courierId").value(7))
            .andExpect(jsonPath("$.courierName").value("Amal"))
            .andExpect(jsonPath("$.state").value("ACTIVE"));

        org.mockito.Mockito.verify(dataClient).assignParcelCourier(PARCEL_ONE, 70L);
        mockMvc.perform(get("/api/dispatch/parcels/unassigned").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].parcelId").value(PARCEL_TWO.toString()));
        mockMvc.perform(get("/api/dispatch/couriers").header("Authorization", token("ADMIN", "1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].name").value("Amal"))
            .andExpect(jsonPath("$[0].activeParcelCount").value(1))
            .andExpect(jsonPath("$[1].activeParcelCount").value(0))
            .andExpect(jsonPath("$[2].availability").value("BUSY"))
            .andExpect(jsonPath("$[2].available").value(false));
        mockMvc.perform(get("/api/dispatch/assignments?state=ACTIVE").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-0001"))
            .andExpect(jsonPath("$.content[0].parcelStatus").value("CREATED"));
    }

    @Test
    void assignmentFailsWhenAlreadyAssignedNotCreatedOrCourierUnavailable() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        assign(PARCEL_ONE, 9)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Parcel already has an active assignment"));
        assign(PARCEL_TWO, 11)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Courier must be active and AVAILABLE"));
        assign(PARCEL_TWO, 999).andExpect(status().isBadRequest());
        givenParcel(PARCEL_TWO, "SD-0002", "PICKED_UP");
        assign(PARCEL_TWO, 9)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Parcel is not waiting for a courier"));
        assertThat(count("SELECT count(*) FROM dispatch_assignments")).isEqualTo(1);
    }

    @Test
    void assignmentIsRolledBackWhenParcelServiceRefuses() throws Exception {
        doThrow(new DispatchException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is unavailable"))
            .when(dataClient).assignParcelCourier(any(), any());

        assign(PARCEL_ONE, 7).andExpect(status().isServiceUnavailable());

        assertThat(count("SELECT count(*) FROM dispatch_assignments")).isZero();
    }

    @Test
    void reassignAndCancelWorkOnlyBeforePickup() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        mockMvc.perform(post("/api/dispatch/assignments/{id}/reassign", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER", "42"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":9}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierId").value(9))
            .andExpect(jsonPath("$.previousCourierId").value(7));
        org.mockito.Mockito.verify(dataClient).assignParcelCourier(PARCEL_ONE, 90L);
        assertThat(count("SELECT count(*) FROM dispatch_assignments WHERE state = 'CANCELLED' AND version = 1"))
            .isEqualTo(1);

        mockMvc.perform(post("/api/dispatch/assignments/{id}/cancel", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("CANCELLED"));
        mockMvc.perform(get("/api/dispatch/parcels/unassigned").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(jsonPath("$.totalElements").value(2));

        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        givenParcel(PARCEL_ONE, "SD-0001", "PICKED_UP");
        mockMvc.perform(post("/api/dispatch/assignments/{id}/reassign", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER", "42"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":9}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/dispatch/assignments/{id}/cancel", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isConflict());
    }

    @Test
    void parcelDetailCombinesParcelServiceDataWithStoredEventsAndAssignments() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        dispatch.applyStatusEvent(event(PARCEL_ONE, "70", "CREATED", "PICKED_UP", Instant.now()));

        mockMvc.perform(get("/api/dispatch/parcels/{id}", PARCEL_ONE).header("Authorization", token("ADMIN", "1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parcel.trackingNumber").value("SD-0001"))
            .andExpect(jsonPath("$.history[0].newStatus").value("PICKED_UP"))
            .andExpect(jsonPath("$.assignments[0].courierName").value("Amal"))
            .andExpect(jsonPath("$.assignments[0].parcelStatus").value("PICKED_UP"));
    }

    // ------------------------------------------------------------ courier hand-off

    @Test
    void courierReadsOnlyTheirOwnAssignmentsInTheCourierServiceShape() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        mockMvc.perform(post("/api/dispatch/assignments/{id}/reassign", PARCEL_ONE)
                .header("Authorization", token("DISPATCHER", "42"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":9}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/dispatch/couriers/9/assignments").header("Authorization", token("COURIER", "90")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].parcelId").value(PARCEL_ONE.toString()))
            .andExpect(jsonPath("$[0].courierId").value(9))
            .andExpect(jsonPath("$[0].attemptNo").value(2))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].version").value(0))
            .andExpect(jsonPath("$[0].deliveryDate").exists())
            .andExpect(jsonPath("$[0].assignedAt").exists());
        mockMvc.perform(get("/api/dispatch/couriers/7/assignments").header("Authorization", token("COURIER", "70")))
            .andExpect(jsonPath("$[0].attemptNo").value(1))
            .andExpect(jsonPath("$[0].active").value(false))
            .andExpect(jsonPath("$[0].version").value(1));
        mockMvc.perform(get("/api/dispatch/couriers/9/assignments").header("Authorization", token("COURIER", "70")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dispatch/couriers/9/assignments").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/dispatch/couriers/9/assignments").header("Authorization", token("CUSTOMER", "5")))
            .andExpect(status().isForbidden());
    }

    @Test
    void completingAnAssignmentDoesNotChangeItsVersion() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        dispatch.applyStatusEvent(event(PARCEL_ONE, "70", "OUT_FOR_DELIVERY", "DELIVERED", Instant.now()));

        mockMvc.perform(get("/api/dispatch/couriers/7/assignments").header("Authorization", token("COURIER", "70")))
            .andExpect(jsonPath("$[0].active").value(false))
            .andExpect(jsonPath("$[0].version").value(0));
    }

    // ------------------------------------------------------------ events & deliveries

    @Test
    void sameEventIdTwiceIsStoredAndCountedOnce() throws Exception {
        Instant now = Instant.now();
        ParcelStatusChangedEvent delivered = event(PARCEL_ONE, "70", "OUT_FOR_DELIVERY", "DELIVERED", now);

        assertThat(dispatch.applyStatusEvent(delivered)).isTrue();
        assertThat(dispatch.applyStatusEvent(delivered)).isFalse();

        assertThat(count("SELECT count(*) FROM dispatch_report_events")).isEqualTo(1);
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/dispatch/reports/summary?from=" + today + "&to=" + today)
                .header("Authorization", token("ADMIN", "1")))
            .andExpect(jsonPath("$.deliveriesPerDay[0].delivered").value(1))
            .andExpect(jsonPath("$.countsByStatus[0].count").value(1));
    }

    @Test
    void finalEventsCompleteOrCancelTheActiveAssignment() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        assign(PARCEL_TWO, 9).andExpect(status().isCreated());

        dispatch.applyStatusEvent(event(PARCEL_ONE, "70", "OUT_FOR_DELIVERY", "FAILED", Instant.now()));
        dispatch.applyStatusEvent(event(PARCEL_TWO, null, "CREATED", "CANCELLED", Instant.now()));

        assertThat(jdbc.queryForObject("SELECT state FROM dispatch_assignments WHERE parcel_id = ?",
            String.class, PARCEL_ONE)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT state FROM dispatch_assignments WHERE parcel_id = ?",
            String.class, PARCEL_TWO)).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM dispatch_assignments WHERE state = 'ACTIVE'")).isZero();
    }

    @Test
    void deliveriesShowTheLatestStatusPerParcelWithFilters() throws Exception {
        assign(PARCEL_ONE, 7).andExpect(status().isCreated());
        Instant t = Instant.now().minusSeconds(60);
        UUID third = UUID.randomUUID();
        dispatch.applyStatusEvent(event(PARCEL_ONE, "70", "CREATED", "PICKED_UP", t));
        dispatch.applyStatusEvent(event(PARCEL_ONE, "70", "PICKED_UP", "IN_TRANSIT", t.plusSeconds(10)));
        dispatch.applyStatusEvent(event(PARCEL_TWO, "90", "CREATED", "PICKED_UP", t.plusSeconds(5)));
        dispatch.applyStatusEvent(event(third, "90", "OUT_FOR_DELIVERY", "DELIVERED", t.plusSeconds(20)));

        mockMvc.perform(get("/api/dispatch/deliveries").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].parcelId").value(PARCEL_ONE.toString()))
            .andExpect(jsonPath("$.content[0].status").value("IN_TRANSIT"))
            .andExpect(jsonPath("$.content[0].courierName").value("Amal"))
            .andExpect(jsonPath("$.content[0].lastUpdatedAt").exists());
        mockMvc.perform(get("/api/dispatch/deliveries?status=DELIVERED").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].parcelId").value(third.toString()));
        mockMvc.perform(get("/api/dispatch/deliveries?trackingNumber=" + trackingFor(PARCEL_TWO))
                .header("Authorization", token("DISPATCHER", "42")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].status").value("PICKED_UP"));
        mockMvc.perform(get("/api/dispatch/deliveries?status=LOST").header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ reports

    @Test
    void summaryGivesTheExpectedNumbersForASampleSetOfEvents() throws Exception {
        // Courier names come from dispatch's own assignment rows.
        jdbc.update("""
            INSERT INTO dispatch_assignments (assignment_id, parcel_id, tracking_number, courier_profile_id,
                courier_user_id, courier_name, assigned_by, assigned_at, state)
            VALUES (gen_random_uuid(), gen_random_uuid(), 'SD-E', 7, 70, 'Amal', '42', '2026-10-30T08:00:00Z', 'ACTIVE'),
                   (gen_random_uuid(), gen_random_uuid(), 'SD-G', 9, 90, 'Bimal', '42', '2026-10-30T08:00:00Z', 'ACTIVE')
            """);
        Instant nov1 = Instant.parse("2026-11-01T10:00:00Z");
        Instant nov2 = Instant.parse("2026-11-02T10:00:00Z");
        Instant nov3 = Instant.parse("2026-11-03T10:00:00Z");
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "70", "OUT_FOR_DELIVERY", "DELIVERED", nov1));         // A
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "70", "OUT_FOR_DELIVERY", "FAILED", nov1));            // B
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "90", "OUT_FOR_DELIVERY", "DELIVERED", nov2));         // C
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "90", "OUT_FOR_DELIVERY", "DELIVERED", nov2));         // D
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "70", "CREATED", "PICKED_UP", nov2));                  // E
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "70", "IN_TRANSIT", "OUT_FOR_DELIVERY", nov3));        // F
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "90", "PICKED_UP", "IN_TRANSIT", nov3));               // G
        dispatch.applyStatusEvent(event(UUID.randomUUID(), "70", "OUT_FOR_DELIVERY", "DELIVERED",
            Instant.parse("2026-10-31T10:00:00Z")));                                                               // H

        mockMvc.perform(get("/api/dispatch/reports/summary?from=2026-11-01&to=2026-11-03")
                .header("Authorization", token("DISPATCHER", "42")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveriesPerDay", hasSize(3)))
            .andExpect(jsonPath("$.deliveriesPerDay[0].date").value("2026-11-01"))
            .andExpect(jsonPath("$.deliveriesPerDay[0].delivered").value(1))
            .andExpect(jsonPath("$.deliveriesPerDay[0].failed").value(1))
            .andExpect(jsonPath("$.deliveriesPerDay[0].total").value(2))
            .andExpect(jsonPath("$.deliveriesPerDay[1].delivered").value(2))
            .andExpect(jsonPath("$.deliveriesPerDay[1].failed").value(0))
            .andExpect(jsonPath("$.deliveriesPerDay[2].date").value("2026-11-03"))
            .andExpect(jsonPath("$.deliveriesPerDay[2].total").value(0))
            .andExpect(jsonPath("$.successRate").value(75.0))
            .andExpect(jsonPath("$.failureRate").value(25.0))
            .andExpect(jsonPath("$.countsByStatus", hasSize(5)))
            .andExpect(jsonPath("$.countsByStatus[?(@.status=='DELIVERED')].count").value(3))
            .andExpect(jsonPath("$.countsByStatus[?(@.status=='FAILED')].count").value(1))
            .andExpect(jsonPath("$.countsByStatus[?(@.status=='PICKED_UP')].count").value(1))
            .andExpect(jsonPath("$.countsByStatus[?(@.status=='OUT_FOR_DELIVERY')].count").value(1))
            .andExpect(jsonPath("$.countsByStatus[?(@.status=='IN_TRANSIT')].count").value(1))
            .andExpect(jsonPath("$.activeDeliveriesPerCourier", hasSize(2)))
            .andExpect(jsonPath("$.activeDeliveriesPerCourier[0].courierId").value(7))
            .andExpect(jsonPath("$.activeDeliveriesPerCourier[0].courierName").value("Amal"))
            .andExpect(jsonPath("$.activeDeliveriesPerCourier[0].count").value(2))
            .andExpect(jsonPath("$.activeDeliveriesPerCourier[1].courierId").value(9))
            .andExpect(jsonPath("$.activeDeliveriesPerCourier[1].count").value(1));
    }

    @Test
    void summaryRejectsAnInvalidRange() throws Exception {
        mockMvc.perform(get("/api/dispatch/reports/summary?from=2026-11-03&to=2026-11-01")
                .header("Authorization", token("ADMIN", "1")))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ helpers

    private ResultActions assign(UUID parcelId, long courierId) throws Exception {
        return mockMvc.perform(post("/api/dispatch/assignments")
            .header("Authorization", token("DISPATCHER", "42"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parcelId\":\"" + parcelId + "\",\"courierId\":" + courierId + "}"));
    }

    private void givenParcel(UUID id, String tracking, String status) {
        when(dataClient.findParcel(id)).thenReturn(Optional.of(parcel(id, tracking, status)));
    }

    private static ParcelRow parcel(UUID id, String tracking, String status) {
        Instant created = Instant.parse("2026-11-01T08:00:00Z");
        return new ParcelRow(id, tracking, "Colombo", "Kandy", "Colombo 03", "Kandy Town", "SMALL", status,
            null, new BigDecimal("850.00"), created, created);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static String trackingFor(UUID parcelId) {
        return "SD-EV-" + parcelId.toString().substring(24);
    }

    private static ParcelStatusChangedEvent event(UUID parcelId, String courierUserId, String oldStatus,
            String newStatus, Instant occurredAt) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), "ParcelStatusChanged", occurredAt,
            trackingFor(parcelId), parcelId.toString(), null, null, "5", courierUserId, oldStatus, newStatus);
    }

    private static String token(String role, String subject) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder()
            .subject(subject)
            .claim("email", "user@example.com")
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900)))
            .signWith(Keys.hmacShaKeyFor(KEY))
            .compact();
    }
}
