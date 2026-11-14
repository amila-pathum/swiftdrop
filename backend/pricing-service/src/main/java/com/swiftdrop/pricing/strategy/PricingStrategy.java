package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

public interface PricingStrategy {

    BigDecimal calculate(
            BigDecimal currentAmount,
            PricingContext context
    );
}