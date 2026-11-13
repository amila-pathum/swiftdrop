package com.swiftdrop.merchant.dto.response;

public record MerchantWebhookWithSecretResponse(
        MerchantWebhookResponse settings,
        String signingSecret
) {}
