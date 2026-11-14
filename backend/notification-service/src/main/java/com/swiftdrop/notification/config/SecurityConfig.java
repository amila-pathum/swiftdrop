package com.swiftdrop.notification.config;

import org.springframework.security.core.GrantedAuthority;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Every endpoint requires a valid JWT issued by the Identity Service, except
 * {@code GET /actuator/health}. This service verifies the token itself with the
 * shared JWT_SECRET; it does not rely on the API Gateway having checked it.
 *
 * <p>The token's {@code role} claim becomes the authority {@code ROLE_<role>} when
 * its {@code status} claim is {@code ACTIVE} (accounts awaiting approval get none), so
 * endpoints declare who may call them with, for example,
 * {@code @PreAuthorize("hasRole('MERCHANT')")}. The caller's user id is the token
 * subject: {@code @AuthenticationPrincipal Jwt jwt}, then {@code jwt.getSubject()}.
 *
 * <p>Unlike the Identity Service, this service cannot see the user's current
 * account status, so a token stays usable here until it expires
 * (JWT_EXPIRATION_MS, 15 minutes by default) even if the account is suspended.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, exception) ->
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                exception instanceof OAuth2AuthenticationException
                    ? "Invalid or expired access token" : "Authentication is required",
                objectMapper);
        AccessDeniedHandler denied = (request, response, exception) ->
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Access is denied", objectMapper);

        return http
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/notifications/parcels/*/stream").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(unauthenticated)
                .accessDeniedHandler(denied))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(unauthenticated)
                .accessDeniedHandler(denied))
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${swiftdrop.jwt.secret:}") String encodedSecret) {
        return decoderFor(encodedSecret);
    }

    /** Verifies tokens exactly as the Identity Service signs them. */
    static JwtDecoder decoderFor(String encodedSecret) {
        if (!StringUtils.hasText(encodedSecret)) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedSecret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", exception);
        }
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        }
        // The Identity Service (jjwt) picks the HMAC strength from the key length.
        MacAlgorithm algorithm = key.length >= 64 ? MacAlgorithm.HS512
            : key.length >= 48 ? MacAlgorithm.HS384 : MacAlgorithm.HS256;

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(key, "Hmac" + algorithm.getName().replace("HS", "SHA")))
            .macAlgorithm(algorithm)
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(),
            new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
            new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText),
            new JwtClaimValidator<String>("role", StringUtils::hasText)));
        return decoder;
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Only ACTIVE accounts hold their role. A merchant or courier awaiting
        // approval can sign in, but its token (status PENDING_APPROVAL) grants no
        // authority, so every hasRole(...) check refuses it. Tokens issued before
        // the claim existed have no status and were only ever given to ACTIVE accounts.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String status = jwt.getClaimAsString("status");
            return status == null || "ACTIVE".equals(status)
                ? roles.convert(jwt)
                : List.<GrantedAuthority>of();
        });
        return converter;
    }

    private static void writeError(HttpServletResponse response, int status, String message,
            ObjectMapper objectMapper) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", message));
    }
}
