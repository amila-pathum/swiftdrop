package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.entity.AuthProvider;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.GoogleSignInException;
import com.swiftdrop.identity_service.exception.InvalidLoginCodeException;
import com.swiftdrop.identity_service.repository.OAuthLoginCodeRepository;
import com.swiftdrop.identity_service.repository.UserIdentityRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.service.GoogleAccountService.GoogleProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Google account resolution and one-time login codes against real PostgreSQL,
 * where uniqueness, nullability and the atomic "mark used" update matter.
 * Each service call commits on its own, as in production.
 */
@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "DB_PASSWORD=unused-with-service-connection"
})
@Testcontainers(disabledWithoutDocker = true)
class GoogleSignInPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    private static final String VERIFIER = "frontend-generated-verifier-0123456789-abcdefghij";
    private static final Instant START = Instant.parse("2026-06-01T12:00:00Z");
    private static final MutableClock clock = new MutableClock(START);

    // Built by Spring so @Transactional applies exactly as in production.
    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        OAuthLoginCodeService clockedCodeService(OAuthLoginCodeRepository repository) {
            return new OAuthLoginCodeService(repository, clock);
        }
    }

    @Autowired GoogleAccountService googleAccounts;
    @Autowired OAuthLoginCodeService codeService;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired OAuthLoginCodeRepository codes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE oauth_login_codes, user_identities, users RESTART IDENTITY CASCADE");
        clock.reset(START);
    }

    private static String challengeFor(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private User customer() {
        return users.save(new User("Customer", "customer@example.com", "hash",
            Role.CUSTOMER, UserStatus.ACTIVE));
    }

    @Test
    void googleSignInCreatesALinkedCustomerWithNoPassword() {
        User created = googleAccounts.resolveUser(
            new GoogleProfile("sub-1", "New@Gmail.com", true, "New User"));

        assertThat(jdbc.queryForObject(
            "SELECT password_hash IS NULL FROM users WHERE id = ?", Boolean.class, created.getId()))
            .isTrue();
        var identity = identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-1");
        assertThat(identity).isPresent();
        assertThat(identity.get().getUser().getEmail()).isEqualTo("new@gmail.com");

        User again = googleAccounts.resolveUser(
            new GoogleProfile("sub-1", "renamed@gmail.com", true, "Renamed"));
        assertThat(again.getId()).isEqualTo(created.getId());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void googleSignInDoesNotTouchAnExistingPasswordAccount() {
        customer();

        assertThatThrownBy(() -> googleAccounts.resolveUser(
            new GoogleProfile("sub-2", "customer@example.com", true, "Attacker Name")))
            .isInstanceOf(GoogleSignInException.class);

        assertThat(identities.count()).isZero();
        assertThat(jdbc.queryForMap("SELECT name, password_hash FROM users"))
            .containsEntry("name", "Customer").containsEntry("password_hash", "hash");
    }

    @Test
    void storesOnlyAHashOfTheCode() throws Exception {
        String code = codeService.issue(customer(), challengeFor(VERIFIER));

        assertThat(code).matches("[A-Za-z0-9_-]{43}");
        String stored = jdbc.queryForObject("SELECT code_hash FROM oauth_login_codes", String.class);
        assertThat(stored).isNotEqualTo(code).isEqualTo(HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void redeemsOnceWithTheMatchingVerifier() throws Exception {
        User user = customer();
        String code = codeService.issue(user, challengeFor(VERIFIER));

        assertThat(codeService.redeem(code, VERIFIER).getId()).isEqualTo(user.getId());
        assertThatThrownBy(() -> codeService.redeem(code, VERIFIER))
            .isInstanceOf(InvalidLoginCodeException.class);
    }

    @Test
    void aWrongVerifierFailsAndBurnsTheCode() throws Exception {
        String code = codeService.issue(customer(), challengeFor(VERIFIER));

        assertThatThrownBy(() -> codeService.redeem(code, "attacker-guess"))
            .isInstanceOf(InvalidLoginCodeException.class);
        assertThatThrownBy(() -> codeService.redeem(code, VERIFIER))
            .isInstanceOf(InvalidLoginCodeException.class);
    }

    @Test
    void expiresAfterSixtySeconds() throws Exception {
        String code = codeService.issue(customer(), challengeFor(VERIFIER));

        clock.advance(Duration.ofSeconds(60));

        assertThatThrownBy(() -> codeService.redeem(code, VERIFIER))
            .isInstanceOf(InvalidLoginCodeException.class);
    }

    @Test
    void isStillValidJustBeforeExpiry() throws Exception {
        User user = customer();
        String code = codeService.issue(user, challengeFor(VERIFIER));

        clock.advance(Duration.ofSeconds(59));

        assertThat(codeService.redeem(code, VERIFIER).getId()).isEqualTo(user.getId());
    }

    @Test
    void rejectsUnknownAndMissingCodes() {
        assertThatThrownBy(() -> codeService.redeem("unknown-code", VERIFIER))
            .isInstanceOf(InvalidLoginCodeException.class);
        assertThatThrownBy(() -> codeService.redeem(null, VERIFIER))
            .isInstanceOf(InvalidLoginCodeException.class);
        assertThatThrownBy(() -> codeService.redeem("code", " "))
            .isInstanceOf(InvalidLoginCodeException.class);
    }

    @Test
    void rejectsAMalformedChallengeWhenIssuing() {
        User user = customer();
        assertThatThrownBy(() -> codeService.issue(user, "too-short"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(codes.count()).isZero();
    }

    @Test
    void removesExpiredCodesWhenIssuingNewOnes() throws Exception {
        User user = customer();
        codeService.issue(user, challengeFor(VERIFIER));
        clock.advance(Duration.ofMinutes(5));

        codeService.issue(user, challengeFor(VERIFIER));

        assertThat(codes.count()).isEqualTo(1);
    }

    @Test
    void concurrentRedemptionsSucceedOnlyOnce() throws Exception {
        String code = codeService.issue(customer(), challengeFor(VERIFIER));
        int attempts = 8;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            start.await();
            try {
                codeService.redeem(code, VERIFIER);
                return true;
            } catch (InvalidLoginCodeException exception) {
                return false;
            }
        };

        try (var executor = Executors.newFixedThreadPool(attempts)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(attempt));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        }
    }

    /** A clock tests can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void reset(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
