package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Express multiplies everything before it by the route's express multiplier. */
@Order(4)
@Component
public class ExpressPricingStrategy implements PricingStrategy {

    @Override
    public String breakdownKey() {
        return "expressFee";
    }

    @Override
    public BigDecimal calculate(BigDecimal currentAmount, PricingContext context) {
        if (!context.express()) {
            return currentAmount;
        }
        return currentAmount.multiply(context.route().getExpressMultiplier());
    }
}
