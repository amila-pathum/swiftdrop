package com.swiftdrop.notification.dto.response;

import com.swiftdrop.notification.entity.Notification;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record NotificationResponse(
    UUID id,
    String userId,
    String trackingNumber,
    String title,
    String message,
    boolean isRead,
    Instant createdAt
) {
    public boolean getRead() {
        return isRead;
    }

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
            .id(n.getId())
            .userId(n.getUserId())
            .trackingNumber(n.getTrackingNumber())
            .title(n.getTitle())
            .message(n.getMessage())
            .isRead(n.isRead())
            .createdAt(n.getCreatedAt())
            .build();
    }
}
