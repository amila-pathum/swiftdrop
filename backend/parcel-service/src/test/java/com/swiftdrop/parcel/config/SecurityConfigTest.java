package com.swiftdrop.parcel.config;

import com.swiftdrop.parcel.service.ParcelService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT validation and role checks, with tokens signed exactly as the Identity
 * Service signs them (jjwt). Runs without a database or Docker.
 */
@SpringBootTest(properties = {
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "management.health.rabbit.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final byte[] KEY = bytes(32);
    private static final byte[] OTHER_KEY = bytes(32, 100);

    @Autowired MockMvc mockMvc;

    @MockitoBean ParcelService parcelService;
    @MockitoBean com.swiftdrop.parcel.service.ParcelQueryService parcelQueryService;
    @MockitoBean com.swiftdrop.parcel.service.ParcelCreationService parcelCreationService;
    @MockitoBean com.swiftdrop.parcel.service.ParcelStatusUpdateService parcelStatusUpdateService;
    @MockitoBean com.swiftdrop.parcel.event.ParcelEventOutbox parcelEventOutbox;
    @MockitoBean com.swiftdrop.parcel.service.ParcelStatusIdempotencyStore parcelStatusIdempotencyStore;

    /** Test-only endpoint showing the convention for a protected, role-restricted endpoint. */
    @TestConfiguration
    static class ExampleEndpoint {
        @Bean
        ExampleController exampleController() {
            return new ExampleController();
        }
    }

    @RestController
    static class ExampleController {
        @GetMapping("/test/security/merchant-only")
        @PreAuthorize("hasRole('MERCHANT')")
        Map<String, String> merchantOnly(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("userId", jwt.getSubject());
        }
    }

    private static byte[] bytes(int length) {
        return bytes(length, 0);
    }

    private static byte[] bytes(int length, int offset) {
        byte[] key = new byte[length];
        IntStream.range(0, length).forEach(i -> key[i] = (byte) (i + offset));
        return key;
    }

    /** Built like Identity Service JwtService.generateToken. */
    private static JwtBuilder identityToken(String role) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
            .subject("42")
            .claim("email", "user@example.com")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900)));
        return role == null ? builder : builder.claim("role", role);
    }

    private static String signed(JwtBuilder builder, byte[] key) {
        return "Bearer " + builder.signWith(Keys.hmacShaKeyFor(key)).compact();
    }

    @Test
    void healthIsPublicAndShowsNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/info", "/api/anything", "/"})
    void everythingElseRequiresAToken(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Authentication is required"));
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposedEvenWithAToken() throws Exception {
        mockMvc.perform(get("/actuator/env").header("Authorization", signed(identityToken("ADMIN"), KEY)))
            .andExpect(status().isNotFound());
    }

    @Test
    void theRequiredRoleIsAllowedAndSeesItsUserId() throws Exception {
        mockMvc.perform(get("/test/security/merchant-only")
                .header("Authorization", signed(identityToken("MERCHANT"), KEY)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("42"));
    }

    @Test
    void anAccountAwaitingApprovalIsAuthenticatedButHoldsNoRole() throws Exception {
        for (String status : new String[] {"PENDING_APPROVAL", "SUSPENDED", "DISABLED"}) {
            mockMvc.perform(get("/test/security/merchant-only").header("Authorization",
                    signed(identityToken("MERCHANT").claim("status", status), KEY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access is denied"));
        }
        mockMvc.perform(get("/test/security/merchant-only").header("Authorization",
                signed(identityToken("MERCHANT").claim("status", "ACTIVE"), KEY)))
            .andExpect(status().isOk());
    }

    @Test
    void anotherRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/test/security/merchant-only")
                .header("Authorization", signed(identityToken("CUSTOMER"), KEY)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Access is denied"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "9223372036854775807"})
    void customerCreationAcceptsIdentityNumericSubject(String subject) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/parcels")
                .header("Authorization", signed(identityToken("CUSTOMER").subject(subject), KEY))
                .contentType("application/json")
                .content("""
                    {"receiverName":"Nimal","receiverPhone":"0771234567",
                     "pickupAddressLine1":"10 Main Street","pickupCity":"Colombo",
                     "deliveryAddressLine1":"20 Lake Road","deliveryCity":"Kandy",
                     "weight":2.5,"size":"MEDIUM","parcelType":"STANDARD"}
                    """))
            .andExpect(status().isCreated());
        org.mockito.Mockito.verify(parcelCreationService).createCustomerParcel(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Long.valueOf(subject)),
            org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"0", "-1", "01", "+1", "9223372036854775808", "550e8400-e29b-41d4-a716-446655440000"})
    void creationRejectsNonIdentitySubjects(String subject) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/parcels")
                .header("Authorization", signed(identityToken("CUSTOMER").subject(subject), KEY))
                .contentType("application/json")
                .content("""
                    {"receiverName":"Nimal","receiverPhone":"0771234567",
                     "pickupAddressLine1":"10 Main Street","pickupCity":"Colombo",
                     "deliveryAddressLine1":"20 Lake Road","deliveryCity":"Kandy",
                     "weight":2.5,"size":"MEDIUM","parcelType":"STANDARD"}
                    """))
            .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(parcelCreationService);
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String[] tokens = {
            "Bearer not-a-jwt",
            signed(identityToken("MERCHANT"), OTHER_KEY),
            signed(identityToken("MERCHANT").issuedAt(Date.from(past.minusSeconds(900)))
                .expiration(Date.from(past)), KEY),
            signed(identityToken(null), KEY),
            signed(identityToken("MERCHANT").subject(null), KEY),
            "Bearer " + identityToken("MERCHANT").compact(),
        };
        for (String token : tokens) {
            mockMvc.perform(get("/test/security/merchant-only").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 48, 64})
    void acceptsEverySecretLengthTheIdentityServiceCanSignWith(int length) {
        byte[] key = bytes(length);
        String token = signed(identityToken("COURIER"), key).substring("Bearer ".length());

        var decoded = SecurityConfig.decoderFor(Base64.getEncoder().encodeToString(key)).decode(token);

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("COURIER");
    }

    @Test
    void refusesAMissingOrWeakSecret() {
        assertThatThrownBy(() -> SecurityConfig.decoderFor(" "))
            .hasMessage("JWT_SECRET must be configured");
        assertThatThrownBy(() -> SecurityConfig.decoderFor("not base64!"))
            .hasMessage("JWT_SECRET must be valid Base64");
        assertThatThrownBy(() -> SecurityConfig.decoderFor(Base64.getEncoder().encodeToString(bytes(16))))
            .hasMessage("JWT_SECRET must contain at least 256 bits");
    }

    @Test
    void customerListingUsesJwtIdentityAndBoundedPagination() throws Exception {
        mockMvc.perform(get("/api/parcels/my?page=2&size=5&userId=999&role=ADMIN")
            .header("Authorization", signed(identityToken("CUSTOMER"), KEY)))
            .andExpect(status().isOk());
        org.mockito.Mockito.verify(parcelQueryService).getMyParcels(42L,
            org.springframework.data.domain.PageRequest.of(2, 5,
                org.springframework.data.domain.Sort.by("createdAt").descending()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc"})
    void invalidPaginationReturnsBadRequest(String query) throws Exception {
        mockMvc.perform(get("/api/parcels/my?" + query)
            .header("Authorization", signed(identityToken("CUSTOMER"), KEY)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/parcels?status=CREATED&" + query)
            .header("Authorization", signed(identityToken("ADMIN"), KEY)))
            .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(parcelQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "ADMIN", "DISPATCHER"})
    void detailsAndHistoryForwardVerifiedIdentity(String role) throws Exception {
        var id = java.util.UUID.randomUUID();
        mockMvc.perform(get("/api/parcels/" + id)
            .header("Authorization", signed(identityToken(role), KEY))).andExpect(status().isOk());
        mockMvc.perform(get("/api/parcels/" + id + "/history")
            .header("Authorization", signed(identityToken(role), KEY))).andExpect(status().isOk());
        org.mockito.Mockito.verify(parcelQueryService).getParcelById(id, 42L, role);
        org.mockito.Mockito.verify(parcelQueryService).getParcelHistory(id, 42L, role);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DISPATCHER"})
    void staffListingUsesStatusAndAscendingDefaultPage(String role) throws Exception {
        mockMvc.perform(get("/api/parcels?status=CREATED")
            .header("Authorization", signed(identityToken(role), KEY))).andExpect(status().isOk());
        org.mockito.Mockito.verify(parcelQueryService).getParcelsByStatus(
            com.swiftdrop.parcel.entity.ParcelStatus.CREATED, role,
            org.springframework.data.domain.PageRequest.of(0, 10,
                org.springframework.data.domain.Sort.by("createdAt").ascending()));
    }

    @Test
    void getEndpointsRejectMissingTokensAndDisallowedRoles() throws Exception {
        String[] paths = {"/api/parcels/my", "/api/parcels?status=CREATED",
            "/api/parcels/00000000-0000-0000-0000-000000000001",
            "/api/parcels/00000000-0000-0000-0000-000000000001/history"};
        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : new String[]{"MERCHANT", "COURIER"}) {
                mockMvc.perform(get(path).header("Authorization", signed(identityToken(role), KEY)))
                    .andExpect(status().isForbidden());
            }
            mockMvc.perform(get(path).header("Authorization",
                signed(identityToken(path.contains("status=") ? "ADMIN" : "CUSTOMER").subject("bad"), KEY)))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/parcels?status=CREATED")
            .header("Authorization", signed(identityToken("CUSTOMER"), KEY)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parcels/my")
            .header("Authorization", signed(identityToken("ADMIN"), KEY)))
            .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(parcelQueryService);
    }

    @Test
    void invalidStatusAndParcelIdReturnBadRequest() throws Exception {
        for (String path : new String[]{"/api/parcels", "/api/parcels?status=INVALID", "/api/parcels/not-a-uuid"}) {
            mockMvc.perform(get(path).header("Authorization", signed(identityToken("ADMIN"), KEY)))
                .andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verifyNoInteractions(parcelQueryService);
    }
}
