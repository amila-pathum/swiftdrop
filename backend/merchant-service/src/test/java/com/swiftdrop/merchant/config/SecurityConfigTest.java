package com.swiftdrop.merchant.config;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

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
 * Tests JWT validation and role-based security without
 * connecting to a database or starting merchant services.
 */
@SpringBootTest(
    classes = SecurityConfigTest.TestApplication.class,
    properties = {
        "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration"
    }
)
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final byte[] KEY = bytes(32);
    private static final byte[] OTHER_KEY = bytes(32, 100);

    @Autowired
    MockMvc mockMvc;

    /**
     * Minimal Spring Boot configuration for security tests.
     * Does not scan MerchantService, repositories, or controllers.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        SecurityConfig.class,
        ExampleController.class
    })
    static class TestApplication {
    }

    /**
     * Test-only endpoint protected by MERCHANT role.
     */
    @RestController
    static class ExampleController {

        @GetMapping("/test/security/merchant-only")
        @PreAuthorize("hasRole('MERCHANT')")
        Map<String, String> merchantOnly(
                @AuthenticationPrincipal Jwt jwt) {

            return Map.of("userId", jwt.getSubject());
        }
    }

    private static byte[] bytes(int length) {
        return bytes(length, 0);
    }

    private static byte[] bytes(int length, int offset) {

        byte[] key = new byte[length];

        IntStream.range(0, length)
                .forEach(i -> key[i] = (byte) (i + offset));

        return key;
    }

    /**
     * Creates tokens in the same format as Identity Service.
     */
    private static JwtBuilder identityToken(String role) {

        Instant now = Instant.now();

        JwtBuilder builder = Jwts.builder()
                .subject("42")
                .claim("email", "user@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)));

        return role == null
                ? builder
                : builder.claim("role", role);
    }

    private static String signed(
            JwtBuilder builder,
            byte[] key) {

        return "Bearer " +
                builder.signWith(
                        Keys.hmacShaKeyFor(key)
                ).compact();
    }

    @Test
    void healthIsPublicAndShowsNoDetails() throws Exception {

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator/env",
        "/actuator/info",
        "/api/anything",
        "/"
    })
    void everythingElseRequiresAToken(String path)
            throws Exception {

        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("Authentication is required"));
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposedEvenWithAToken()
            throws Exception {

        mockMvc.perform(
                get("/actuator/env")
                        .header(
                                "Authorization",
                                signed(identityToken("ADMIN"), KEY)
                        )
        ).andExpect(status().isNotFound());
    }

    @Test
    void theRequiredRoleIsAllowedAndSeesItsUserId()
            throws Exception {

        mockMvc.perform(
                get("/test/security/merchant-only")
                        .header(
                                "Authorization",
                                signed(identityToken("MERCHANT"), KEY)
                        )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("42"));
    }

    @Test
    void anotherRoleIsForbidden() throws Exception {

        mockMvc.perform(
                get("/test/security/merchant-only")
                        .header(
                                "Authorization",
                                signed(identityToken("CUSTOMER"), KEY)
                        )
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error")
                .value("Access is denied"));
    }

    @Test
    void invalidTokensAreRejected() throws Exception {

        Instant past = Instant.now().minusSeconds(3600);

        String[] tokens = {
            "Bearer not-a-jwt",

            signed(identityToken("MERCHANT"), OTHER_KEY),

            signed(
                identityToken("MERCHANT")
                    .issuedAt(
                        Date.from(past.minusSeconds(900))
                    )
                    .expiration(Date.from(past)),
                KEY
            ),

            signed(identityToken(null), KEY),

            signed(
                identityToken("MERCHANT").subject(null),
                KEY
            ),

            "Bearer " +
                identityToken("MERCHANT").compact()
        };

        for (String token : tokens) {

            mockMvc.perform(
                    get("/test/security/merchant-only")
                            .header("Authorization", token)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error")
                    .value("Invalid or expired access token"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 48, 64})
    void acceptsEverySecretLengthTheIdentityServiceCanSignWith(
            int length) {

        byte[] key = bytes(length);

        String token = signed(
                identityToken("COURIER"),
                key
        ).substring("Bearer ".length());

        var decoded = SecurityConfig.decoderFor(
                Base64.getEncoder().encodeToString(key)
        ).decode(token);

        assertThat(decoded.getSubject())
                .isEqualTo("42");

        assertThat(decoded.getClaimAsString("role"))
                .isEqualTo("COURIER");
    }

    @Test
    void refusesAMissingOrWeakSecret() {

        assertThatThrownBy(
                () -> SecurityConfig.decoderFor(" ")
        ).hasMessage("JWT_SECRET must be configured");

        assertThatThrownBy(
                () -> SecurityConfig.decoderFor("not base64!")
        ).hasMessage("JWT_SECRET must be valid Base64");

        assertThatThrownBy(
                () -> SecurityConfig.decoderFor(
                    Base64.getEncoder().encodeToString(
                        bytes(16)
                    )
                )
        ).hasMessage(
                "JWT_SECRET must contain at least 256 bits"
        );
    }
}
