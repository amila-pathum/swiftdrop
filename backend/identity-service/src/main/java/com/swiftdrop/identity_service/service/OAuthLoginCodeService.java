package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.entity.OAuthLoginCode;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.exception.InvalidLoginCodeException;
import com.swiftdrop.identity_service.repository.OAuthLoginCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.regex.Pattern;

/**
 * Issues and redeems the one-time codes that carry a completed OAuth sign-in
 * from the backend callback to the frontend, so a JWT never appears in a URL.
 *
 * <p>Each code is 256 random bits, valid for {@link #TTL}, usable once, and bound
 * to a challenge (base64url SHA-256 of a verifier the frontend keeps). Only the
 * code's SHA-256 hash is stored.
 */
@Service
public class OAuthLoginCodeService {

    static final Duration TTL = Duration.ofSeconds(60);

    // base64url, no padding, of a 32-byte SHA-256 digest.
    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final OAuthLoginCodeRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public OAuthLoginCodeService(OAuthLoginCodeRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OAuthLoginCodeService(OAuthLoginCodeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public static boolean isValidChallenge(String challenge) {
        return challenge != null && CHALLENGE.matcher(challenge).matches();
    }

    /** Returns the raw code; it is not stored and cannot be recovered later. */
    @Transactional
    public String issue(User user, String clientChallenge) {
        if (!isValidChallenge(clientChallenge)) {
            throw new IllegalArgumentException("Invalid client challenge");
        }
        Instant now = clock.instant();
        repository.deleteExpired(now);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = BASE64URL.encodeToString(bytes);
        repository.save(new OAuthLoginCode(sha256Hex(code), user, clientChallenge,
            now, now.plus(TTL)));
        return code;
    }

    /**
     * Redeems a code and returns its user. The code is consumed by the first
     * attempt even if the verifier is wrong, so it can never be tried twice.
     */
    @Transactional(noRollbackFor = InvalidLoginCodeException.class)
    public User redeem(String code, String verifier) {
        if (code == null || code.isBlank() || verifier == null || verifier.isBlank()) {
            throw new InvalidLoginCodeException();
        }
        String codeHash = sha256Hex(code);
        if (repository.markUsed(codeHash, clock.instant()) != 1) {
            throw new InvalidLoginCodeException();
        }
        OAuthLoginCode loginCode = repository.findByCodeHash(codeHash)
            .orElseThrow(InvalidLoginCodeException::new);

        byte[] expected = loginCode.getClientChallenge().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = BASE64URL.encodeToString(sha256(verifier))
            .getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidLoginCodeException();
        }
        return loginCode.getUser();
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
