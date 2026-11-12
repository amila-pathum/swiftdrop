package com.swiftdrop.notification.webhook.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureTest {

    private static final String SECRET = "webhook-test-secret";
    private static final long TIMESTAMP = 1794499200L;
    private static final Instant NOW =
            Instant.ofEpochSecond(TIMESTAMP);

    private static final byte[] BODY = (
            "{\"eventId\":\"evt-test-1\",\"newStatus\":\"DELIVERED\"}"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    void signatureMatchesIndependentTestVector() {
        assertEquals(
                "sha256="
                        + "ad2e413ddbb2454da5b60459e29cfafb71"
                        + "6b914b4921a8dabd205819a4c3e0e2",
                WebhookSignature.sign(SECRET, TIMESTAMP, BODY)
        );
    }

    @Test
    void originalMessageIsAccepted() {
        String signature =
                WebhookSignature.sign(SECRET, TIMESTAMP, BODY);

        assertTrue(WebhookSignature.verify(
                SECRET, TIMESTAMP, BODY, signature, NOW
        ));
    }

    @Test
    void modifiedBodyIsRejected() {
        String signature =
                WebhookSignature.sign(SECRET, TIMESTAMP, BODY);

        byte[] changedBody = (
                "{\"eventId\":\"evt-test-1\",\"newStatus\":\"FAILED\"}"
        ).getBytes(StandardCharsets.UTF_8);

        assertFalse(WebhookSignature.verify(
                SECRET, TIMESTAMP, changedBody, signature, NOW
        ));
    }

    @Test
    void wrongSecretIsRejected() {
        String signature =
                WebhookSignature.sign(SECRET, TIMESTAMP, BODY);

        assertFalse(WebhookSignature.verify(
                "different-test-secret",
                TIMESTAMP, BODY, signature, NOW
        ));
    }

    @Test
    void expiredTimestampIsRejected() {
        String signature =
                WebhookSignature.sign(SECRET, TIMESTAMP, BODY);

        assertFalse(WebhookSignature.verify(
                SECRET, TIMESTAMP, BODY, signature,
                NOW.plusSeconds(301)
        ));
    }

    @Test
    void changedTimestampIsRejected() {
        String signature =
                WebhookSignature.sign(SECRET, TIMESTAMP, BODY);

        assertFalse(WebhookSignature.verify(
                SECRET, TIMESTAMP + 1, BODY, signature, NOW
        ));
    }

    @Test
    void malformedSignatureIsRejected() {
        assertFalse(WebhookSignature.verify(
                SECRET, TIMESTAMP, BODY, "sha256=invalid", NOW
        ));
    }
}