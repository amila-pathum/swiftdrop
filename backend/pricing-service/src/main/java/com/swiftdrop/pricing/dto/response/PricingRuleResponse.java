package com.swiftdrop.pricing.dto.response;

import com.swiftdrop.pricing.entity.PricingRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingRuleResponse(
        UUID id,
        UUID zoneId,
        String zoneName,
        BigDecimal baseFee,
        BigDecimal pricePerKg,
        BigDecimal expressMultiplier,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PricingRuleResponse from(PricingRule rule) {
        return new PricingRuleResponse(
                rule.getId(),
                rule.getZone().getId(),
                rule.getZone().getName(),
                rule.getBaseFee(),
                rule.getPricePerKg(),
                rule.getExpressMultiplier(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
