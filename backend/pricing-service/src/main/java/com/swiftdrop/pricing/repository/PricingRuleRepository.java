
package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "zone")
    Optional<PricingRule> findByZone_IdAndActiveTrue(UUID zoneId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "zone")
    List<PricingRule> findByActiveTrue();

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "zone")
    List<PricingRule> findAll();

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "zone")
    Optional<PricingRule> findById(UUID id);

    boolean existsByZone_IdAndActiveTrue(UUID zoneId);
    
}
