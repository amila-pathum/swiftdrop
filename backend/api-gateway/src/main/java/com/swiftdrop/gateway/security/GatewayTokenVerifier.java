package com.swiftdrop.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies access tokens exactly as the Identity Service signs them: HMAC with
 * the shared Base64 {@code JWT_SECRET}, strength chosen from the key length.
 *
 * <p>Only ordinary user access tokens are accepted. Short-lived service tokens
 * (those with an audience or a {@code purpose} claim, such as the merchant
 * order integration token) are for direct service-to-service calls and are
 * rejected here, so they cannot be replayed through the public entry point.
 */
@Component
public class GatewayTokenVerifier {

    static final Set<String> ROLES =
        Set.of("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN");

    /** Same tolerance as the services' Nimbus decoders. */
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final SecretKey key;

    public GatewayTokenVerifier(@Value("${swiftdrop.jwt.secret:}") String encodedSecret) {
        if (!StringUtils.hasText(encodedSecret)) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedSecret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", exception);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** Returns the caller, or empty when the token is missing, invalid or expired. */
    public Optional<GatewayPrincipal> verify(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            boolean serviceToken = claims.get("purpose") != null
                || (claims.getAudience() != null && !claims.getAudience().isEmpty());
            if (!StringUtils.hasText(subject) || !subject.chars().allMatch(Character::isDigit)
                    || role == null || !ROLES.contains(role)
                    || claims.getExpiration() == null || serviceToken) {
                return Optional.empty();
            }
            return Optional.of(new GatewayPrincipal(
                subject, role, claims.get("status", String.class)));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
