package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Charges for weight above the route's base weight, per started kilogram: with a 1 kg
 * base weight, 2.3 kg is charged as 2 additional kg.
 */
@Order(2)
@Component
public class WeightBasedPricingStrategy implements PricingStrategy {

    @Override
    public String breakdownKey() {
        return "weightFee";
    }

    @Override
    public BigDecimal calculate(BigDecimal currentAmount, PricingContext context) {
        BigDecimal extra = context.weight().subtract(context.route().getBaseWeightKg());
        if (extra.signum() <= 0) {
            return currentAmount;
        }
        BigDecimal additionalKg = extra.setScale(0, RoundingMode.CEILING);
        return currentAmount.add(additionalKg.multiply(context.route().getAdditionalKgFee()));
    }
}
