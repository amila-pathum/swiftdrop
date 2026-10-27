package com.swiftdrop.identity_service.security;

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
}
