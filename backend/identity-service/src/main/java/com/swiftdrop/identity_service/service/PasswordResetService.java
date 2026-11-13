package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.PasswordResetTokenResponse;
import com.swiftdrop.identity_service.entity.PasswordResetToken;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.InvalidResetTokenException;
import com.swiftdrop.identity_service.exception.PasswordResetUnavailableException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.repository.PasswordResetTokenRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Locale;

/**
 * Password reset with single-use tokens.
 *
 * <p>Each token is 256 random bits, valid for {@link #TTL} and usable once.
 * Only its SHA-256 hash is stored. Issuing a token removes the user's earlier
 * ones; a successful reset also signs the user out everywhere.
 */
@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(30);
    static final String RESET_PATH = "/reset-password#token=";

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetDelivery delivery;
    private final String frontendOrigin;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
            PasswordEncoder passwordEncoder, ObjectProvider<PasswordResetDelivery> delivery,
            @Value("${swiftdrop.frontend-origin}") String frontendOrigin) {
        this(tokens, users, passwordEncoder, delivery.getIfAvailable(), frontendOrigin,
            Clock.systemUTC());
    }

    PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
            PasswordEncoder passwordEncoder, PasswordResetDelivery delivery,
            String frontendOrigin, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.delivery = delivery;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
        this.clock = clock;
    }

    /**
     * Issues a token for a user chosen by an administrator, who passes it on
     * out of band. The raw token is returned once and never stored.
     */
    @Transactional
    public PasswordResetTokenResponse issueFor(long userId) {
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!user.getStatus().canSignIn()) {
            throw new AccountNotActiveException();
        }
        return issue(user);
    }

    /**
     * Self-service request by email. Returns normally whether or not the email is
     * registered, so the response cannot be used to find accounts. Refused while
     * there is no way to deliver the link.
     */
    @Transactional
    public void requestByEmail(String email) {
        if (delivery == null) {
            throw new PasswordResetUnavailableException();
        }
        users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .filter(user -> user.getStatus().canSignIn())
            .ifPresent(user -> {
                PasswordResetTokenResponse issued = issue(user);
                delivery.send(user, issued.resetUrl(), issued.expiresAt());
            });
    }

    /**
     * Sets a new password. The token is consumed by the first attempt, so it can
     * never be used twice, even concurrently.
     */
    @Transactional(noRollbackFor = InvalidResetTokenException.class)
    public void confirm(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new InvalidResetTokenException();
        }
        String tokenHash = sha256Hex(token);
        if (tokens.markUsed(tokenHash, clock.instant()) != 1) {
            throw new InvalidResetTokenException();
        }
        User user = tokens.findByTokenHash(tokenHash)
            .map(PasswordResetToken::getUser)
            .filter(candidate -> candidate.getStatus().canSignIn())
            .orElseThrow(InvalidResetTokenException::new);

        user.updatePassword(passwordEncoder.encode(newPassword));
        user.invalidateTokens();
        users.save(user);
    }

    private PasswordResetTokenResponse issue(User user) {
        Instant now = clock.instant();
        tokens.deleteExpired(now);
        tokens.deleteByUserId(user.getId());

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = BASE64URL.encodeToString(bytes);
        Instant expiresAt = now.plus(TTL);
        tokens.save(new PasswordResetToken(sha256Hex(token), user, now, expiresAt));
        return new PasswordResetTokenResponse(token, expiresAt,
            frontendOrigin + RESET_PATH + token);
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
