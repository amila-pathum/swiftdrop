package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {
    List<MerchantApiKey> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    Optional<MerchantApiKey> findByIdAndMerchantId(Long id, Long merchantId);
    Optional<MerchantApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
