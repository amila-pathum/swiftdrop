package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantWebhookSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MerchantWebhookSettingsRepository
        extends JpaRepository<MerchantWebhookSettings, Long> {
    Optional<MerchantWebhookSettings> findByMerchantId(Long merchantId);
}
