package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.RefreshRequest;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.InvalidRefreshTokenException;
import com.swiftdrop.identity_service.repository.RefreshTokenRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.security.AuthenticatedUser;
import com.swiftdrop.identity_service.security.JwtService;
import com.swiftdrop.identity_service.service.GoogleSignInPersistenceTest.MutableClock;
import com.swiftdrop.identity_service.service.RefreshTokenService.IssuedRefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refresh token issue, rotation, reuse detection and revocation against real
 * PostgreSQL. Each service call commits on its own, as in production.
 */
@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "DB_PASSWORD=unused-with-service-connection"
})
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    private static final Instant START = Instant.parse("2026-06-01T12:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final Duration REMEMBER_ME_TTL = Duration.ofDays(30);
    private static final MutableClock clock = new MutableClock(START);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        RefreshTokenService clockedRefreshTokenService(RefreshTokenRepository repository) {
            return new RefreshTokenService(repository, SESSION_TTL, REMEMBER_ME_TTL, clock);
        }
    }

    @Autowired RefreshTokenService refreshTokens;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE refresh_tokens, users RESTART IDENTITY CASCADE");
        clock.reset(START);
    }

    private User user(String email, Role role, UserStatus status) {
        return users.save(new User("Test User", email, passwordEncoder.encode("password123"),
            role, status));
    }

    private int storedTokens() {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class);
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() {
        IssuedRefreshToken issued = refreshTokens.issue(
            user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE), false);

        String stored = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(issued.token());
        assertThat(issued.token()).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void rememberMeChoosesTheLongerLifetime() {
        User user = user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE);

        assertThat(refreshTokens.issue(user, false).expiresAt()).isEqualTo(START.plus(SESSION_TTL));
        assertThat(refreshTokens.issue(user, true).expiresAt()).isEqualTo(START.plus(REMEMBER_ME_TTL));
        assertThat(refreshTokens.issue(user, true).expiresInSeconds())
            .isEqualTo(REMEMBER_ME_TTL.toSeconds());
    }

    @Test
    void rotationReplacesTheTokenWithoutExtendingTheSession() {
        User user = user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE);
        IssuedRefreshToken first = refreshTokens.issue(user, false);
        clock.advance(Duration.ofHours(1));

        var rotation = refreshTokens.rotate(first.token());

        assertThat(rotation.user().getId()).isEqualTo(user.getId());
        assertThat(rotation.refreshToken().token()).isNotEqualTo(first.token());
        assertThat(rotation.refreshToken().expiresAt()).isEqualTo(first.expiresAt());
        assertThat(rotation.refreshToken().expiresInSeconds())
            .isEqualTo(SESSION_TTL.minusHours(1).toSeconds());
    }

    @Test
    void aTokenCanBeUsedOnlyOnce() {
        User user = user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE);
        IssuedRefreshToken first = refreshTokens.issue(user, false);
        IssuedRefreshToken second = refreshTokens.rotate(first.token()).refreshToken();

        assertThatThrownBy(() -> refreshTokens.rotate(first.token()))
            .isInstanceOf(InvalidRefreshTokenException.class);
        // Within the grace period the replacement keeps working (two tabs racing).
        assertThat(refreshTokens.rotate(second.token()).user().getId()).isEqualTo(user.getId());
    }

    @Test
    void reuseAfterTheGracePeriodRevokesTheWholeSignInButNotOtherDevices() {
        User user = user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE);
        IssuedRefreshToken stolen = refreshTokens.issue(user, false);
        IssuedRefreshToken otherDevice = refreshTokens.issue(user, false);
        IssuedRefreshToken legitimate = refreshTokens.rotate(stolen.token()).refreshToken();
        clock.advance(RefreshTokenService.REUSE_GRACE.plusSeconds(1));

        assertThatThrownBy(() -> refreshTokens.rotate(stolen.token()))
            .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokens.rotate(legitimate.token()))
            .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(refreshTokens.rotate(otherDevice.token()).user().getId())
            .isEqualTo(user.getId());
    }

    @Test
    void expiredAndUnknownTokensAreRefused() {
        IssuedRefreshToken issued = refreshTokens.issue(
            user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE), false);
        clock.advance(SESSION_TTL);

        assertThatThrownBy(() -> refreshTokens.rotate(issued.token()))
            .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokens.rotate("not-a-real-token"))
            .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokens.rotate(" "))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void invalidatingTheUsersTokensAlsoEndsRefresh() {
        User user = user("a@example.com", Role.MERCHANT, UserStatus.PENDING_APPROVAL);
        IssuedRefreshToken issued = refreshTokens.issue(user, true);

        // As AdminUserService.activate does when a merchant is approved.
        user.changeStatus(UserStatus.ACTIVE);
        user.invalidateTokens();
        users.save(user);

        assertThatThrownBy(() -> refreshTokens.rotate(issued.token()))
            .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(storedTokens()).isZero();
    }

    @Test
    void suspendedAccountsCannotRefresh() {
        User user = user("a@example.com", Role.COURIER, UserStatus.ACTIVE);
        IssuedRefreshToken issued = refreshTokens.issue(user, false);
        user.changeStatus(UserStatus.SUSPENDED);
        users.save(user);

        assertThatThrownBy(() -> refreshTokens.rotate(issued.token()))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void concurrentRefreshesWithOneTokenHaveExactlyOneWinner() throws Exception {
        IssuedRefreshToken issued = refreshTokens.issue(
            user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE), false);
        int attempts = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(attempts)) {
            Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    refreshTokens.rotate(issued.token());
                    return true;
                } catch (InvalidRefreshTokenException expected) {
                    return false;
                }
            };
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(attempt));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    @Test
    void loginRefreshAndLogoutWorkEndToEndForEveryRole() {
        for (Role role : Role.values()) {
            String email = role.name().toLowerCase() + "@example.com";
            User user = user(email, role, UserStatus.ACTIVE);

            var login = authService.login(new LoginRequest(email, "password123", false));
            var refreshed = authService.refresh(new RefreshRequest(login.refreshToken()));

            AuthenticatedUser claims = jwtService.parseToken(refreshed.accessToken());
            assertThat(claims).isEqualTo(new AuthenticatedUser(user.getId(), email, role));
            assertThat(refreshed.user().role()).isEqualTo(role);
            assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());

            authService.logout(user.getId());
            assertThatThrownBy(() -> authService.refresh(
                new RefreshRequest(refreshed.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        }
        assertThat(storedTokens()).isZero();
    }

    @Test
    void deletingAUserDeletesTheirTokens() {
        User user = user("a@example.com", Role.CUSTOMER, UserStatus.ACTIVE);
        refreshTokens.issue(user, false);

        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());

        assertThat(storedTokens()).isZero();
    }
}
