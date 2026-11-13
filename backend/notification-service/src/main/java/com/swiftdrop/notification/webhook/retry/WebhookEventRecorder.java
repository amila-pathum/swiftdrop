package com.swiftdrop.notification.webhook.retry;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/** RabbitMQ consumer calls record before acknowledging its event.
 * This batch does not add a RabbitMQ consumer or modify Member 10's queues. */
@Service
public class WebhookEventRecorder {
    private final WebhookDeliveryStore store;
    private final ObjectMapper mapper;
    public WebhookEventRecorder(WebhookDeliveryStore store, ObjectMapper mapper) {
        this.store=store;
        this.mapper=mapper;
    }
    public Optional<UUID> record(UUID eventId, Instant occurredAt, String merchantId,
            String trackingNumber, String reference, String newStatus) {
        if (merchantId == null || merchantId.isBlank()) return Optional.empty();
        if (eventId == null || occurredAt == null || trackingNumber == null
                || trackingNumber.isBlank() || newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("Incomplete parcel status event");
        }
        var payload=new LinkedHashMap<String,Object>();
        payload.put("eventId",eventId.toString());
        payload.put("eventType","ParcelStatusChanged");
        payload.put("occurredAt",occurredAt.toString());
        payload.put("trackingNumber",trackingNumber);
        payload.put("merchantOrderReference",reference);
        payload.put("newStatus",newStatus);
        byte[] bytes=mapper.writeValueAsBytes(payload);
        return Optional.of(store.enqueue(eventId,merchantId,trackingNumber,reference,
                newStatus,bytes,Instant.now()));
    }
}
