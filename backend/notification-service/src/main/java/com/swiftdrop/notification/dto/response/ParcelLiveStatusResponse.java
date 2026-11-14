package com.swiftdrop.notification.dto.response;

import lombok.Builder;

import java.time.Instant;

/**
 * Public payload sent over the public tracking stream.
 * In accordance with privacy rules, strictly contains only tracking number,
 * status, and timestamp - never customer names, courier IDs, or personal data.
 */
@Builder
public record ParcelLiveStatusResponse(
    String trackingNumber,
    String status,
    Instant occurredAt
) {
    public Instant getUpdatedAt() {
        return occurredAt;
    }
}

