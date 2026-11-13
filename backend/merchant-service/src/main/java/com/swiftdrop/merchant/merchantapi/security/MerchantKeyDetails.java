package com.swiftdrop.merchant.merchantapi.security;

public record MerchantKeyDetails(
        String merchantId,
        boolean approved
) {
}