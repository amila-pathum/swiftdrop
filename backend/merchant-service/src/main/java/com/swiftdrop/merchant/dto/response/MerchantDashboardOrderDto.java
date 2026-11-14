package com.swiftdrop.merchant.dto.response;

import java.util.List;

public record MerchantDashboardOrderDto(
        String id,
        String trackingNumber,
        String customerName,
        String destination,
        String pickupAddress,
        String deliveryAddress,
        String status,
        String createdAt,
        List<MerchantOrderStatusHistoryDto> statusHistory
) {
}

