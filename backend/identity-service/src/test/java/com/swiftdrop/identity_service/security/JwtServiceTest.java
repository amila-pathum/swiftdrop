package com.swiftdrop.identity_service.security;

import java.util.Date;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Jwts;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        jwtService = new JwtService(secret, 900_000);
        user = new User("Test User", "user@example.com", "hash",
            Role.CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    void generatedTokenValidatesAndContainsSubjectAndRole() {
        var claims = jwtService.parseToken(jwtService.generateToken(user));
        assertThat(claims.id()).isEqualTo(42L);
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void modifiedTokenFailsValidation() {
        String token = jwtService.generateToken(user);
        char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
        String modified = token.substring(0, token.length() - 1) + replacement;
        assertThatThrownBy(() -> jwtService.parseToken(modified))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenFailsValidation() {
        String token = jwtService.generateToken(user, Instant.now().minusSeconds(901));
        assertThatThrownBy(() -> jwtService.parseToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void weakSecretFailsFast() {
        String weak = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new JwtService(weak, 900_000))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("256 bits");
    }

    @Test
    void tokenCarriesTheAccountStatusAndTokenVersionFromTheUser() {
        User pending = new User("Pending Merchant", "merchant@example.com", "hash",
            Role.MERCHANT, UserStatus.PENDING_APPROVAL);
        ReflectionTestUtils.setField(pending, "id", 7L);
        pending.invalidateTokens();
        pending.invalidateTokens();

        TokenClaims claims = jwtService.parseClaims(jwtService.generateToken(pending));

        assertThat(claims.principal())
            .isEqualTo(new AuthenticatedUser(7L, "merchant@example.com", Role.MERCHANT));
        assertThat(claims.status()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(claims.tokenVersion()).isEqualTo(2);
    }

    @Test
    void tokenIssuedBeforeTheStatusAndVersionClaimsStillParses() {
        String legacy = Jwts.builder()
            .subject("42")
            .claim("email", "user@example.com")
            .claim("role", "CUSTOMER")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(new byte[32]))
            .compact();

        TokenClaims claims = jwtService.parseClaims(legacy);

        assertThat(claims.status()).isNull();
        assertThat(claims.tokenVersion()).isZero();
    }

    @Test
    void tokenSignedWithAnotherKeyFailsValidation() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String forged = Jwts.builder()
            .subject("42")
            .claim("email", "user@example.com")
            .claim("role", "ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(otherKey))
            .compact();

        assertThatThrownBy(() -> jwtService.parseToken(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenFailsValidation() {
        String unsigned = Jwts.builder()
            .subject("42")
            .claim("email", "user@example.com")
            .claim("role", "ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .compact();

        assertThatThrownBy(() -> jwtService.parseToken(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithAnUnknownRoleOrStatusFailsValidation() {
        for (String[] claim : new String[][] {{"role", "SUPERUSER"}, {"status", "DELETED"}}) {
            var builder = Jwts.builder()
                .subject("42")
                .claim("email", "user@example.com")
                .claim("role", "CUSTOMER")
                .claim("status", "ACTIVE")
                .expiration(Date.from(Instant.now().plusSeconds(60)));
            String token = builder.claim(claim[0], claim[1])
                .signWith(Keys.hmacShaKeyFor(new byte[32])).compact();

            assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(JwtException.class);
        }
    }
}
