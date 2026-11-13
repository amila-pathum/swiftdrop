package com.swiftdrop.notification.webhook.retry;

import java.util.UUID;

/** Contains only status-event data, not addresses, phone numbers or secrets. */
public record WebhookDelivery(UUID id, UUID eventId, String merchantId,
                              byte[] payload, int attemptCount) {
    public WebhookDelivery {
        payload = payload.clone();
    }
    @Override public byte[] payload() { return payload.clone(); }
    @Override public String toString() {
        return "WebhookDelivery[id=" + id + ", attemptCount=" + attemptCount + "]";
    }
}
