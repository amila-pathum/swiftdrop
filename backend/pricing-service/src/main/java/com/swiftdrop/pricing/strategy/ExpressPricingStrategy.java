package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Order(3)
@Component
public class ExpressPricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculate(
            BigDecimal currentAmount,
            PricingContext context
    ) {
        if (!context.express()) {
            return currentAmount;
        }

        BigDecimal multiplier =
                context.pricingRule().getExpressMultiplier();

        return currentAmount.multiply(multiplier);
    }
}