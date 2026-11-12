package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.MerchantApiKey;
import java.time.OffsetDateTime;

public record MerchantApiKeyResponse(
        Long id,
        String name,
        String prefix,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt
) {
    public static MerchantApiKeyResponse from(MerchantApiKey key) {
        return new MerchantApiKeyResponse(key.getId(), key.getKeyName(),
                key.getKeyPrefix(), key.getCreatedAt(), key.getLastUsedAt(),
                key.getRevokedAt());
    }
}
