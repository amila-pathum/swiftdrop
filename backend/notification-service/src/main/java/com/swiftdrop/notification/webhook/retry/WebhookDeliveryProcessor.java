package com.swiftdrop.notification.webhook.retry;

import com.swiftdrop.notification.webhook.delivery.WebhookHttpSender;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** One bounded delivery attempt; persistence and scheduling are separate. */
public final class WebhookDeliveryProcessor {
    private final MerchantWebhookSettingsLookup settings;
    private final WebhookHttpSender sender;
    private final WebhookRetryPolicy retries;
    private final Clock clock;
    private final Set<String> allowedOrigins;

    public WebhookDeliveryProcessor(MerchantWebhookSettingsLookup settings,
            WebhookHttpSender sender, WebhookRetryPolicy retries,
            Clock clock, Set<String> allowedOrigins) {
        this.settings = settings;
        this.sender = sender;
        this.retries = retries;
        this.clock = clock;
        this.allowedOrigins = allowedOrigins.stream()
                .map(URI::create).map(WebhookDeliveryProcessor::origin)
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("Explicit merchant origins are required");
        }
    }

    public Outcome attempt(WebhookDelivery delivery) throws InterruptedException {
        int attemptNumber = delivery.attemptCount() + 1;
        Instant attemptedAt = clock.instant();
        WebhookHttpSender.SendResult result;
        try {
            var found = settings.findForMerchant(delivery.merchantId());
            if (found.isEmpty() || !found.get().enabled()) {
                return new Outcome("SKIPPED", attemptedAt, null, null, "WEBHOOK_DISABLED");
            }
            var config = found.get();
            URI endpoint = config.endpoint();
            if (!allowedOrigins.contains(origin(endpoint))) {
                return new Outcome("FAILED", attemptedAt, null, null, "ENDPOINT_NOT_ALLOWED");
            }
            result = sender.send(endpoint, config.signingSecret(),
                    delivery.eventId().toString(), delivery.payload());
        } catch (IllegalArgumentException exception) {
            return new Outcome("FAILED", attemptedAt, null, null, "INVALID_SETTINGS");
        } catch (RuntimeException exception) {
            // Never persist/log exception text: internal responses may contain secrets.
            result = new WebhookHttpSender.SendResult(false, null, "DEPENDENCY_ERROR");
        }
        if (result.successful()) {
            return new Outcome("DELIVERED", attemptedAt, null, result.httpStatus(), null);
        }
        if (retries.exhausted(attemptNumber)) {
            return new Outcome("FAILED", attemptedAt, null,
                    result.httpStatus(), result.failureCode());
        }
        return new Outcome("RETRY_SCHEDULED", attemptedAt,
                clock.instant().plus(retries.delayAfter(attemptNumber)),
                result.httpStatus(), result.failureCode());
    }

    private static String origin(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid webhook endpoint");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean localDemo = scheme.equals("http")
                && (host.equals("127.0.0.1") || host.equals("localhost"));
        if (!scheme.equals("https") && !localDemo) {
            throw new IllegalArgumentException("HTTPS is required except for local demos");
        }
        int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        return scheme + "://" + host + ":" + port;
    }

    public record Outcome(String state, Instant attemptedAt, Instant nextAttemptAt,
                          Integer httpStatus, String failureCode) { }
}
