package com.swiftdrop.merchant.dto.response;

public record CreatedMerchantApiKeyResponse(
        MerchantApiKeyResponse apiKey,
        String secretKey
) {}
