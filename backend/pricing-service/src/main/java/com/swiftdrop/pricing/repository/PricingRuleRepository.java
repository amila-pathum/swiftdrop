
package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    Optional<PricingRule> findByZone_IdAndActiveTrue(UUID zoneId);

    List<PricingRule> findByActiveTrue();

    boolean existsByZone_IdAndActiveTrue(UUID zoneId);
    
}
