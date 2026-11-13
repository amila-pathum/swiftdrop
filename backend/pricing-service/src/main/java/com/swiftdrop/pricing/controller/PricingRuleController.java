package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.request.CreatePricingRuleRequest;
import com.swiftdrop.pricing.dto.request.UpdatePricingRuleRequest;
import com.swiftdrop.pricing.dto.response.PricingRuleResponse;
import com.swiftdrop.pricing.entity.PricingRule;
import com.swiftdrop.pricing.service.PricingRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pricing/rules")
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    public PricingRuleController(PricingRuleService pricingRuleService) {
        this.pricingRuleService = pricingRuleService;
    }

    @GetMapping
    public List<PricingRuleResponse> getActivePricingRules() {
        return pricingRuleService.getActivePricingRules()
                .stream()
                .map(PricingRuleResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PricingRuleResponse getPricingRuleById(@PathVariable UUID id) {
        return PricingRuleResponse.from(
                pricingRuleService.getPricingRuleById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRuleResponse createPricingRule(
            @Valid @RequestBody CreatePricingRuleRequest request) {

        PricingRule pricingRule = new PricingRule();
        pricingRule.setBaseFee(request.baseFee());
        pricingRule.setPricePerKg(request.pricePerKg());
        pricingRule.setExpressMultiplier(request.expressMultiplier());

        return PricingRuleResponse.from(
                pricingRuleService.createPricingRule(
                        request.zoneId(), pricingRule));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRuleResponse updatePricingRule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePricingRuleRequest request) {

        PricingRule updatedRule = new PricingRule();
        updatedRule.setBaseFee(request.baseFee());
        updatedRule.setPricePerKg(request.pricePerKg());
        updatedRule.setExpressMultiplier(request.expressMultiplier());

        return PricingRuleResponse.from(
                pricingRuleService.updatePricingRule(id, updatedRule));
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRuleResponse disablePricingRule(@PathVariable UUID id) {
        return PricingRuleResponse.from(
                pricingRuleService.disablePricingRule(id));
    }
}