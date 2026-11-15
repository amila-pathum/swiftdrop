package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Runs every {@link PricingStrategy} in order (Spring sorts them by {@code @Order}). */
@Component
public class PricingCalculator {

    private final List<PricingStrategy> strategies;

    public PricingCalculator(List<PricingStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * Each step is rounded to cents as it is applied, so every breakdown entry is a
     * whole amount of money and the entries add up exactly to the total.
     */
    public PriceCalculation calculate(PricingContext context) {
        BigDecimal amount = BigDecimal.ZERO.setScale(2);
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();

        for (PricingStrategy strategy : strategies) {
            BigDecimal next = strategy.calculate(amount, context).setScale(2, RoundingMode.HALF_UP);
            breakdown.merge(strategy.breakdownKey(), next.subtract(amount), BigDecimal::add);
            amount = next;
        }

        return new PriceCalculation(amount, Collections.unmodifiableMap(breakdown));
    }
}
