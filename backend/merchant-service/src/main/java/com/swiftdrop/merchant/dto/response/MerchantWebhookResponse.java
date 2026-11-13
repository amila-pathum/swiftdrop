package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.MerchantWebhookSettings;
import java.time.OffsetDateTime;

public record MerchantWebhookResponse(String url, boolean enabled, OffsetDateTime updatedAt) {
    public static MerchantWebhookResponse from(MerchantWebhookSettings config) {
        return new MerchantWebhookResponse(config.getWebhookUrl(),
                config.isEnabled(), config.getUpdatedAt());
    }
}
