package com.swiftdrop.merchant.merchantapi.dto;

public record MerchantOrderResponse(

        String merchantOrderReference,

        String trackingNumber,

        String status

) {
}