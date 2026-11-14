package com.swiftdrop.notification.dto.response;

import lombok.Builder;

@Builder
public record UnreadCountResponse(long unreadCount) {
}
