package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.PricingRule;
import java.math.BigDecimal;

public record PricingContext(
        BigDecimal weight,
        PricingRule pricingRule,
        boolean express
) {
}