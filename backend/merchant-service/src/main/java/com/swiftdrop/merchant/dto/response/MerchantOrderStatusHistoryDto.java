package com.swiftdrop.merchant.dto.response;

public record MerchantOrderStatusHistoryDto(
        String status,
        String description,
        String changedAt
) {
}

