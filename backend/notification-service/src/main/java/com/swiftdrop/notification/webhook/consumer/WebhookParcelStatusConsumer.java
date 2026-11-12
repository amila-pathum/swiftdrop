package com.swiftdrop.notification.webhook.consumer;

import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.webhook.retry.WebhookEventRecorder;
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

    public WebhookParcelStatusConsumer(
            ObjectMapper objectMapper,
            WebhookEventRecorder recorder
    ) {
        this.reader = objectMapper
                .readerFor(ParcelStatusChangedEvent.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.recorder = recorder;
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

        // Do not catch database failures here.
        // The listener must fail if durable persistence fails.
        recorder.record(
                event.eventId(),
                event.occurredAt(),
                event.merchantId(),
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
                || !hasText(event.oldStatus())
                || !hasText(event.newStatus())) {
            throw new IllegalArgumentException(
                    "Invalid parcel status changed event"
            );
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}