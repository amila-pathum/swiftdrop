package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Order(1)
@Component
public class ZoneBasedPricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculate(
            BigDecimal currentAmount,
            PricingContext context
    ) {
        BigDecimal baseFee = context.pricingRule().getBaseFee();

        return currentAmount.add(baseFee);
    }
}