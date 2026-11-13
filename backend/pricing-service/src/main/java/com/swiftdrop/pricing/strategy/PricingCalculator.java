package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PricingCalculator {

    private final List<PricingStrategy> strategies;

    public PricingCalculator(List<PricingStrategy> strategies) {
        this.strategies = strategies;
    }

    public BigDecimal calculate(PricingContext context) {

        BigDecimal amount = BigDecimal.ZERO;

        for (PricingStrategy strategy : strategies) {
            amount = strategy.calculate(amount, context);
        }

        return amount;
    }
}