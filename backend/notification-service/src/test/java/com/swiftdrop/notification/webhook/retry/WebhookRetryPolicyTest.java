package com.swiftdrop.notification.webhook.retry;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WebhookRetryPolicyTest {
    @Test void usesGrowingDelays() {
        var policy=new WebhookRetryPolicy();
        assertEquals(Duration.ofMinutes(1),policy.delayAfter(1));
        assertEquals(Duration.ofMinutes(5),policy.delayAfter(2));
        assertEquals(Duration.ofMinutes(15),policy.delayAfter(3));
        assertEquals(Duration.ofMinutes(60),policy.delayAfter(4));
    }
    @Test void fifthAttemptIsLast() {
        var policy=new WebhookRetryPolicy();
        assertFalse(policy.exhausted(4));
        assertTrue(policy.exhausted(5));
        assertThrows(IllegalArgumentException.class,() -> policy.delayAfter(5));
    }
    @Test void rejectsInvalidAttemptNumber() {
        assertThrows(IllegalArgumentException.class,() -> new WebhookRetryPolicy().delayAfter(0));
    }
    @Test void rejectsZeroDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookRetryPolicy(List.of(Duration.ZERO)));
    }
}
