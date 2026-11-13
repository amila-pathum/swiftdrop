package com.swiftdrop.identity_service.security;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies SwiftDrop access tokens (HMAC, key from JWT_SECRET).
 *
 * <p>Claims: {@code sub} (user id), {@code email}, {@code role}, {@code status}
 * (account status at issue), {@code ver} (the user's token version), {@code iat}
 * and {@code exp}. Every claim comes from the database row, never from a request.
 * Other services verify the same signature with the shared secret and must
 * grant {@code ROLE_<role>} only when {@code status} is {@code ACTIVE}.
 */
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_STATUS = "status";
    static final String CLAIM_TOKEN_VERSION = "ver";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String encodedSecret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(encodedSecret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        return generateToken(user, Instant.now());
    }

    String generateToken(User user, Instant issuedAt) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim(CLAIM_EMAIL, user.getEmail())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim(CLAIM_STATUS, user.getStatus().name())
            .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusMillis(expirationMs)))
            .signWith(signingKey)
            .compact();
    }

    /** Verifies the signature and expiry and returns who the token belongs to. */
    public AuthenticatedUser parseToken(String token) throws JwtException {
        return parseClaims(token).principal();
    }

    /** As {@link #parseToken}, with the status and token version the token was issued with. */
    public TokenClaims parseClaims(String token) throws JwtException {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        try {
            long userId = Long.parseLong(claims.getSubject());
            String email = claims.get(CLAIM_EMAIL, String.class);
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Missing email claim");
            }
            String status = claims.get(CLAIM_STATUS, String.class);
            Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            return new TokenClaims(
                new AuthenticatedUser(userId, email, role),
                status == null ? null : UserStatus.valueOf(status),
                version == null ? 0 : version);
        } catch (RuntimeException exception) {
            throw new JwtException("Invalid token claims", exception);
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
