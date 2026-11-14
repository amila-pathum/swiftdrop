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
class ParcelReadApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Value("${local.server.port}") int port;
    @Autowired ParcelService creation;
    @Autowired ParcelRepository parcels;
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

    private String token(String role, long userId) {
        Instant now = Instant.now();
        return Jwts.builder().subject(Long.toString(userId)).claim("role", role)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))).compact();
    }

    private HttpResponse<String> get(String path, String role, long userId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(15)).GET();
        if (role != null) request.header("Authorization", "Bearer " + token(role, userId));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void ownerCanReadDetailsAndHistory() throws Exception {
        UUID id = fixture();
        var response = get("/api/parcels/" + id, "CUSTOMER", 42L);
        assertThat(response.statusCode()).isEqualTo(200);
        var json = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertThat(json.get("id").asText()).isEqualTo(id.toString());
        assertThat(json.get("senderId").asLong()).isEqualTo(42L);
        assertThat(get("/api/parcels/" + id + "/history", "CUSTOMER", 42L).statusCode()).isEqualTo(200);
    }

    @Test void otherCustomersCannotReadDetailsOrQueryHistory() throws Exception {
        UUID id = fixture();
        clearInvocations(histories);
        for (String suffix : new String[]{"", "/history"}) {
            var denied = get("/api/parcels/" + id + suffix, "CUSTOMER", 77L);
            var absent = get("/api/parcels/" + UUID.randomUUID() + suffix, "CUSTOMER", 77L);
            assertThat(denied.statusCode()).isEqualTo(404);
            assertThat(absent.statusCode()).isEqualTo(404);
            assertThat(denied.body()).isEqualTo(absent.body());
            assertThat(denied.body()).doesNotContain("Receiver", "0771234567", "Colombo", "Kandy");
        }
        verify(histories, never()).findByParcelIdOrderByChangedAtAsc(any());
    }

    @Test void courierCannotAccessStaffListing() throws Exception {
        assertThat(get("/api/parcels?status=CREATED", "COURIER", 77L).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DISPATCHER"})
    void staffCanReadAnotherCustomersParcel(String role) throws Exception {
        UUID id = fixture();
        assertThat(get("/api/parcels/" + id, role, 77L).statusCode()).isEqualTo(200);
        assertThat(get("/api/parcels/" + id + "/history", role, 77L).statusCode()).isEqualTo(200);
    }

    @Test void unauthenticatedReadsAreRejected() throws Exception {
        UUID id = fixture();
        for (String path : new String[]{"/api/parcels/my", "/api/parcels?status=CREATED",
                "/api/parcels/" + id, "/api/parcels/" + id + "/history"}) {
            assertThat(get(path, null, 0L).statusCode()).isEqualTo(401);
        }
    }

    @Test void customerListingIgnoresForgedIdentityAndRoleParameters() throws Exception {
        UUID id = fixture();
        var response = get("/api/parcels/my?userId=42&senderId=42&role=ADMIN", "CUSTOMER", 77L);
        assertThat(response.statusCode()).isEqualTo(200);
        var json = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertThat(json.get("totalElements").asLong()).isZero();
        assertThat(response.body()).doesNotContain(id.toString());
        var own = get("/api/parcels/my", "CUSTOMER", 42L);
        assertThat(own.statusCode()).isEqualTo(200);
        var ownJson = new tools.jackson.databind.ObjectMapper().readTree(own.body());
        assertThat(ownJson.get("content").size()).isPositive();
        for (var entry : ownJson.get("content")) assertThat(entry.get("senderId").asLong()).isEqualTo(42L);
    }
}
