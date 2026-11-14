package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.entity.RefreshToken;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.exception.InvalidRefreshTokenException;
import com.swiftdrop.identity_service.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens: 256 random bits, single use, rotated on every refresh.
 *
 * <p>Only SHA-256 hashes are stored. A token is accepted only while the user's
 * token version is the one it was issued under, so everything that invalidates
 * access tokens (logout, password change or reset, role or status change) also
 * ends refresh.
 *
 * <p>Presenting an already-used token is treated as theft, whenever it happens:
 * every token from that sign-in (its family) is revoked, so whichever of the
 * thief and the real user refreshed first is cut off too. Other sign-ins of the
 * same user are left alone. The frontend never sends one token twice (one
 * refresh at a time per tab, serialized across tabs), so legitimate use does
 * not trip this.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository tokens;
    private final Duration sessionTtl;
    private final Duration rememberMeTtl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository tokens,
            @Value("${jwt.refresh-expiration-ms}") long sessionTtlMs,
            @Value("${jwt.refresh-remember-me-expiration-ms}") long rememberMeTtlMs) {
        this(tokens, Duration.ofMillis(sessionTtlMs), Duration.ofMillis(rememberMeTtlMs),
            Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository tokens, Duration sessionTtl,
            Duration rememberMeTtl, Clock clock) {
        if (sessionTtl.isNegative() || sessionTtl.isZero()
                || rememberMeTtl.isNegative() || rememberMeTtl.isZero()) {
            throw new IllegalArgumentException("Refresh token lifetimes must be positive");
        }
        this.tokens = tokens;
        this.sessionTtl = sessionTtl;
        this.rememberMeTtl = rememberMeTtl;
        this.clock = clock;
    }

    /** A raw token, returned once to the client, and how long it stays valid. */
    public record IssuedRefreshToken(String token, Instant expiresAt, long expiresInSeconds) {}

    public record Rotation(User user, IssuedRefreshToken refreshToken) {}

    /** Starts a new sign-in. `persistent` ("Remember me") picks the longer lifetime. */
    @Transactional
    public IssuedRefreshToken issue(User user, boolean persistent) {
        Instant now = clock.instant();
        tokens.deleteExpired(now);
        tokens.deleteStale(user.getId(), user.getTokenVersion());
        Instant expiresAt = now.plus(persistent ? rememberMeTtl : sessionTtl);
        return save(user, UUID.randomUUID().toString(), persistent, now, expiresAt);
    }

    /**
     * Consumes a refresh token and returns its replacement in the same family,
     * with the same expiry. The deletes done on reuse or revocation must commit
     * even though the call fails.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        String tokenHash = sha256Hex(rawToken);

        // The single UPDATE also serializes concurrent attempts: a second request
        // with the same token waits for the first to commit, then sees it used.
        if (tokens.markUsed(tokenHash, now) != 1) {
            tokens.findByTokenHash(tokenHash)
                .filter(token -> token.getUsedAt() != null)
                .ifPresent(token -> {
                    tokens.deleteByFamilyId(token.getFamilyId());
                    log.warn("Refresh token reuse detected for user {}; sign-in revoked",
                        token.getUser().getId());
                });
            throw new InvalidRefreshTokenException();
        }

        RefreshToken current = tokens.findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);
        User user = current.getUser();
        if (current.getTokenVersion() != user.getTokenVersion()
                || !user.getStatus().canSignIn()) {
            tokens.deleteByFamilyId(current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }

        IssuedRefreshToken next = save(user, current.getFamilyId(), current.isPersistent(),
            now, current.getExpiresAt());
        return new Rotation(user, next);
    }

    /** Ends refresh for every sign-in of the user. */
    @Transactional
    public void revokeAll(long userId) {
        tokens.deleteByUserId(userId);
    }

    private IssuedRefreshToken save(User user, String familyId, boolean persistent,
            Instant now, Instant expiresAt) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = BASE64URL.encodeToString(bytes);
        tokens.save(new RefreshToken(sha256Hex(token), user, familyId, persistent, now, expiresAt));
        return new IssuedRefreshToken(token, expiresAt,
            Math.max(Duration.between(now, expiresAt).toSeconds(), 1));
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
