package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.entity.PricingRule;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.repository.PricingRuleRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneRepository zoneRepository;

    public PricingRuleService(
            PricingRuleRepository pricingRuleRepository,
            ZoneRepository zoneRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneRepository = zoneRepository;
    }

    public List<PricingRule> getActivePricingRules() {
        return pricingRuleRepository.findByActiveTrue();
    }

    public PricingRule getPricingRuleById(UUID id) {
        return pricingRuleRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Pricing rule not found"));
    }

    public PricingRule createPricingRule(UUID zoneId, PricingRule pricingRule) {

        Zone zone = zoneRepository.findById(zoneId)
                .filter(Zone::isActive)
                .orElseThrow(() ->
                        new RuntimeException("Active zone not found"));

        if (pricingRuleRepository.existsByZone_IdAndActiveTrue(zoneId)) {
            throw new IllegalArgumentException(
                    "An active pricing rule already exists for this zone");
        }

        pricingRule.setZone(zone);

        return pricingRuleRepository.save(pricingRule);
    }

    public PricingRule updatePricingRule(UUID id, PricingRule updatedRule) {

        PricingRule existingRule = getPricingRuleById(id);

        existingRule.setBaseFee(updatedRule.getBaseFee());
        existingRule.setPricePerKg(updatedRule.getPricePerKg());
        existingRule.setExpressMultiplier(
                updatedRule.getExpressMultiplier());

        return pricingRuleRepository.save(existingRule);
    }

    public PricingRule disablePricingRule(UUID id) {

        PricingRule pricingRule = getPricingRuleById(id);

        pricingRule.setActive(false);

        return pricingRuleRepository.save(pricingRule);
    }
}