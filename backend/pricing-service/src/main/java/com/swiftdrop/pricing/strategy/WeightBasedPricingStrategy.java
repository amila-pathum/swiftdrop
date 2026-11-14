package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Order(2)
@Component
public class WeightBasedPricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculate(
            BigDecimal currentAmount,
            PricingContext context
    ) {
        BigDecimal weightCharge = context.weight()
                .multiply(context.pricingRule().getPricePerKg());

        return currentAmount.add(weightCharge);
    }
}