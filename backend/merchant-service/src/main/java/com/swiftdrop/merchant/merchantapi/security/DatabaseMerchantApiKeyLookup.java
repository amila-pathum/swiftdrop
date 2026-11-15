package com.swiftdrop.merchant.merchantapi.security;

import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class DatabaseMerchantApiKeyLookup implements MerchantApiKeyLookup {

    private final MerchantApiKeyRepository keys;

    public DatabaseMerchantApiKeyLookup(MerchantApiKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantKeyDetails> findByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        String hash = MerchantApiKeyService.sha256(apiKey);

        return keys.findByKeyHashAndRevokedAtIsNull(hash)
                .map(key -> {
                    var merchant = key.getMerchant();

                    return new MerchantKeyDetails(
                            merchant.getId().toString(),
                            merchant.getStatus() == MerchantStatus.APPROVED
                    );
                });
    }
}