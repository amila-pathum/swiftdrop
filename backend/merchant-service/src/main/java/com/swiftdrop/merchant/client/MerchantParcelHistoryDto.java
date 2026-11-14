package com.swiftdrop.merchant.client;

import java.time.Instant;

public record MerchantParcelHistoryDto(
        String id,
        String previousStatus,
        String newStatus,
        Instant changedAt,
        String note
) {
}

