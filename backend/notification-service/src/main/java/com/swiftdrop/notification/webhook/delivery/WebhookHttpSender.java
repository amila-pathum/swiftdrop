package com.swiftdrop.notification.webhook.delivery;

import com.swiftdrop.notification.webhook.security.WebhookSignature;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public class WebhookHttpSender {

    private final HttpClient client;
    private final Clock clock;
    private final Duration requestTimeout;

    public WebhookHttpSender(
            Clock clock,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.requestTimeout = requirePositive(requestTimeout);

        this.client = HttpClient.newBuilder()
                .connectTimeout(requirePositive(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public SendResult send(
            URI endpoint,
            String secret,
            String eventId,
            byte[] body
    ) throws InterruptedException {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(body);

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        long timestamp = clock.instant().getEpochSecond();

        String signature = WebhookSignature.sign(
                secret, timestamp, body
        );

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("X-SwiftDrop-Event-Id", eventId)
                .header(
                        "X-SwiftDrop-Timestamp",
                        Long.toString(timestamp)
                )
                .header("X-SwiftDrop-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<Void> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            int status = response.statusCode();

            return new SendResult(
                    status >= 200 && status < 300,
                    status,
                    status >= 200 && status < 300
                            ? null
                            : "HTTP_ERROR"
            );

        } catch (IOException exception) {
            return new SendResult(
                    false,
                    null,
                    "NETWORK_ERROR"
            );
        }
    }

    private static Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    "Timeout must be positive"
            );
        }

        return duration;
    }

    public record SendResult(
            boolean successful,
            Integer httpStatus,
            String failureCode
    ) {
    }
}