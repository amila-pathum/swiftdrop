package com.swiftdrop.notification.webhook.consumer;

import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.webhook.retry.WebhookEventRecorder;
import com.swiftdrop.notification.webhook.retry.MerchantProfileLookup;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Component
@ConditionalOnProperty(
        name = "swiftdrop.webhooks.consumer-enabled",
        havingValue = "true"
)
public class WebhookParcelStatusConsumer {

    private final ObjectReader reader;
    private final WebhookEventRecorder recorder;
    private final MerchantProfileLookup merchants;

    public WebhookParcelStatusConsumer(
            ObjectMapper objectMapper,
            WebhookEventRecorder recorder,
            MerchantProfileLookup merchants
    ) {
        this.reader = objectMapper
                .readerFor(ParcelStatusChangedEvent.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.recorder = recorder;
        this.merchants = merchants;
    }

    @RabbitListener(
            queues = "webhook.parcel-status-changed",
            ackMode = "AUTO"
    )
    public void consume(byte[] message) throws Exception {
        ParcelStatusChangedEvent event = reader.readValue(message);

        validate(event);

        // Customer-only parcels do not need merchant webhooks.
        if (event.merchantId() == null || event.merchantId().isBlank()) {
            return;
        }

        var profileId = merchants.profileForUser(event.merchantId());
        if (profileId.isEmpty()) return;

        // Dependency/database failures must propagate: never fall back to the input user ID.
        // The listener must fail if durable persistence fails.
        recorder.record(
                event.eventId(),
                event.occurredAt(),
                profileId.get(),
                event.trackingNumber(),
                event.merchantOrderReference(),
                event.newStatus()
        );
    }

    private static void validate(ParcelStatusChangedEvent event) {
        if (event == null
                || event.eventId() == null
                || !"ParcelStatusChanged".equals(event.eventType())
                || event.occurredAt() == null
                || !hasText(event.trackingNumber())
                || !hasText(event.parcelId())
                || !hasText(event.customerId())
                || !hasText(event.newStatus())
                || ("CREATED".equals(event.newStatus())
                    ? event.oldStatus() != null
                    : !hasText(event.oldStatus()))) {
            throw new IllegalArgumentException(
                    "Invalid parcel status changed event"
            );
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
