package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.entity.PricingRule;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.repository.PricingRuleRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import com.swiftdrop.pricing.exception.PricingRuleNotFoundException;
import com.swiftdrop.pricing.exception.PricingConflictException;

import java.util.List;
import java.util.UUID;

@Service
@org.springframework.transaction.annotation.Transactional
public class PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneRepository zoneRepository;

    public PricingRuleService(
            PricingRuleRepository pricingRuleRepository,
            ZoneRepository zoneRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneRepository = zoneRepository;
    }

    public List<PricingRule> getAllPricingRules() {
        return pricingRuleRepository.findAll();
    }

    public List<PricingRule> getActivePricingRules() {
        return pricingRuleRepository.findByActiveTrue();
    }

    public PricingRule getPricingRuleById(UUID id) {
        return pricingRuleRepository.findById(id)
                .orElseThrow(() ->
                        new PricingRuleNotFoundException("Pricing rule not found: " + id));
    }

    public PricingRule createPricingRule(UUID zoneId, PricingRule pricingRule) {

        Zone zone = zoneRepository.findById(zoneId)
                .filter(Zone::isActive)
                .orElseThrow(() ->
                        new ZoneNotFoundException(zoneId));

        if (pricingRuleRepository.existsByZone_IdAndActiveTrue(zoneId)) {
            throw new PricingConflictException(
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

    public PricingRule getActivePricingRuleByZoneId(UUID zoneId) {
    return pricingRuleRepository
            .findByZone_IdAndActiveTrue(zoneId)
            .orElseThrow(() ->
                    new PricingRuleNotFoundException(
                            "No active pricing rule for zone " + zoneId));
    }
    
    public PricingRule enablePricingRule(UUID id) {
        PricingRule rule = getPricingRuleById(id);
        if (!rule.getZone().isActive()) {
            throw new PricingConflictException("Enable the zone before enabling its pricing rule");
        }
        if (!rule.isActive() && pricingRuleRepository.existsByZone_IdAndActiveTrue(rule.getZone().getId())) {
            throw new PricingConflictException("An active pricing rule already exists for this zone");
        }
        rule.setActive(true);
        return pricingRuleRepository.save(rule);
    }
}