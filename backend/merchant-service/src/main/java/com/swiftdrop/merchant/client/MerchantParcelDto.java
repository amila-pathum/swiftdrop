package com.swiftdrop.merchant.client;

import java.time.Instant;

public record MerchantParcelDto(
        String id,
        String trackingNumber,
        Long senderId,
        String receiverName,
        String receiverPhone,
        String pickupAddress,
        String deliveryAddress,
        String status,
        String merchantId,
        String merchantOrderReference,
        Instant createdAt
) {
}

