package com.swiftdrop.merchant.merchantapi.dto;

public record MerchantOrderResponse(

        String merchantOrderReference,

        String trackingNumber,

        String status,
        ParcelSnapshot parcel

) {
    public MerchantOrderResponse(String reference, String tracking, String status) { this(reference, tracking, status, null); }
    public record ParcelSnapshot(String id, java.math.BigDecimal charge, java.time.Instant createdAt,
            java.math.BigDecimal weightKg, String size, String serviceType) { }
}
