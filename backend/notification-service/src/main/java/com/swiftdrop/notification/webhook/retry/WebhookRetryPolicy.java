package com.swiftdrop.notification.webhook.retry;

import java.time.Duration;
import java.util.List;

/** One initial attempt plus four retries. Attempts are numbered from one. */
public final class WebhookRetryPolicy {
    private final List<Duration> delays;

    public WebhookRetryPolicy() {
        this(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
                Duration.ofMinutes(15), Duration.ofMinutes(60)));
    }

    public WebhookRetryPolicy(List<Duration> delays) {
        this.delays = List.copyOf(delays);
        if (this.delays.stream().anyMatch(d -> d.isZero() || d.isNegative())) {
            throw new IllegalArgumentException("Retry delays must be positive");
        }
    }

    public boolean exhausted(int completedAttempts) {
        check(completedAttempts);
        return completedAttempts >= delays.size() + 1;
    }

    public Duration delayAfter(int completedAttempts) {
        check(completedAttempts);
        if (exhausted(completedAttempts)) {
            throw new IllegalArgumentException("No attempts remain");
        }
        return delays.get(completedAttempts - 1);
    }

    private void check(int attempts) {
        if (attempts < 1) throw new IllegalArgumentException("Attempt must be positive");
    }
}
