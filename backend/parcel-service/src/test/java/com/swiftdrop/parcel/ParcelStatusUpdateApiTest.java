package com.swiftdrop.parcel;

import com.swiftdrop.parcel.entity.*;
import com.swiftdrop.parcel.repository.*;
import com.swiftdrop.parcel.service.ParcelService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "PARCEL_DB_PASSWORD=unused", "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@Testcontainers
class ParcelStatusUpdateApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Value("${local.server.port}") int port;
    @Autowired ParcelService creation;
    @Autowired ParcelRepository parcels;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean ParcelStatusHistoryRepository histories;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private UUID fixture() {
        Parcel p = new Parcel();
        p.setTrackingNumber("SD" + UUID.randomUUID().toString().replace("-", ""));
        p.setSenderId(42L); p.setCreatedBy(42L);
        p.setReceiverName("Receiver"); p.setReceiverPhone("0771234567");
        p.setPickupAddress("Colombo"); p.setDeliveryAddress("Kandy");
        p.setWeight(new BigDecimal("1.250")); p.setCharge(new BigDecimal("450.00"));
        p.setSize("MEDIUM"); p.setParcelType("STANDARD");
        p.setAssignedCourierId(UUID.randomUUID());
        return creation.createParcel(p).getId();
    }

    private String token(String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject("77").claim("role", role)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))).compact();
    }

    private HttpResponse<String> patch(UUID id, String role, String status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/parcels/" + id + "/status"))
            .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(
                "{\"status\":\"" + status + "\",\"note\":\" collected \"}"));
        if (role != null) request.header("Authorization", "Bearer " + token(role));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void unchanged(UUID id) {
        assertThat(parcels.findById(id).orElseThrow().getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(histories.findByParcelIdOrderByChangedAtAsc(id)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DISPATCHER"})
    void signedPrivilegedTokenUpdatesParcelAndHistory(String role) throws Exception {
        UUID id = fixture();
        var response = patch(id, role, "PICKED_UP");
        assertThat(response.statusCode()).isEqualTo(200);
        var json = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertThat(json.get("parcelId").asText()).isEqualTo(id.toString());
        assertThat(json.get("previousStatus").asText()).isEqualTo("CREATED");
        assertThat(json.get("newStatus").asText()).isEqualTo("PICKED_UP");
        assertThat(json.get("changedAt").asText()).isNotBlank();
        assertThat(parcels.findById(id).orElseThrow().getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        var entries = histories.findByParcelIdOrderByChangedAtAsc(id);
        assertThat(entries).hasSize(2);
        var update = entries.getLast();
        assertThat(update.getPreviousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(update.getNewStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(update.getChangedBy()).isEqualTo(77L);
        assertThat(update.getNote()).isEqualTo("collected");
    }

    @ParameterizedTest @ValueSource(strings = {"DELIVERED", "CANCELLED", "CREATED"})
    void invalidTransitionReturnsConflictWithoutWrites(String status) throws Exception {
        UUID id = fixture();
        assertThat(patch(id, "ADMIN", status).statusCode()).isEqualTo(409);
        unchanged(id);
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "MERCHANT", "COURIER"})
    void unauthorizedRoleOrUnverifiedAssignmentReturnsForbidden(String role) throws Exception {
        UUID id = fixture();
        assertThat(patch(id, role, "PICKED_UP").statusCode()).isEqualTo(403);
        unchanged(id);
    }

    @Test void missingTokenReturnsUnauthorized() throws Exception {
        UUID id = fixture();
        assertThat(patch(id, null, "PICKED_UP").statusCode()).isEqualTo(401);
        unchanged(id);
    }

    @Test void historyFailureAfterFlushRollsBackParcelAndHistory() throws Exception {
        UUID id = fixture();
        doAnswer(call -> {
            entityManager.persist(call.getArgument(0));
            entityManager.flush(); // Flush the dirty parcel and history before simulating failure.
            throw new org.springframework.dao.DataIntegrityViolationException("Simulated failure after flush");
        }).when(histories).saveAndFlush(any(ParcelStatusHistory.class));
        assertThat(patch(id, "ADMIN", "PICKED_UP").statusCode()).isEqualTo(409);
        unchanged(id);
    }

    @Test void concurrentRequestsProduceOneTransitionAndOneConflict() throws Exception {
        UUID id = fixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> update = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return patch(id, "DISPATCHER", "PICKED_UP").statusCode();
            };
            var first = executor.submit(update);
            var second = executor.submit(update);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally { start.countDown(); }
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(parcels.findById(id).orElseThrow().getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        var entries = histories.findByParcelIdOrderByChangedAtAsc(id);
        assertThat(entries).hasSize(2);
        assertThat(entries.getLast().getPreviousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(entries.getLast().getNewStatus()).isEqualTo(ParcelStatus.PICKED_UP);
    }
}
