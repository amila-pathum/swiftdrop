package com.swiftdrop.notification.webhook.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class WebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private WebhookSignature() {
    }

    public static String sign(
            String secret,
            long timestamp,
            byte[] body
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook signing secret must be configured"
            );
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);

            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            ));

            mac.update(
                    (timestamp + ".").getBytes(StandardCharsets.UTF_8)
            );

            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Webhook signing could not be initialized",
                    exception
            );
        }
    }

    public static boolean verify(
            String secret,
            long timestamp,
            byte[] body,
            String signature,
            Instant now
    ) {
        if (signature == null
                || !signature.startsWith(PREFIX)
                || signature.length() != PREFIX.length() + 64) {
            return false;
        }

        try {
            Duration age = Duration.between(
                    Instant.ofEpochSecond(timestamp),
                    now
            ).abs();

            if (age.compareTo(MAX_AGE) > 0) {
                return false;
            }

            byte[] received = HexFormat.of().parseHex(
                    signature.substring(PREFIX.length())
            );

            byte[] expected = HexFormat.of().parseHex(
                    sign(secret, timestamp, body)
                            .substring(PREFIX.length())
            );

            return MessageDigest.isEqual(expected, received);

        } catch (IllegalArgumentException
                 | DateTimeException
                 | ArithmeticException exception) {
            return false;
        }
    }
}