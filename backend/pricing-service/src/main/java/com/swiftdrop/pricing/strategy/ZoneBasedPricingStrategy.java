package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The base fee for the pair of zones the parcel travels between. */
@Order(1)
@Component
public class ZoneBasedPricingStrategy implements PricingStrategy {

    @Override
    public String breakdownKey() {
        return "baseFee";
    }

    @Override
    public BigDecimal calculate(BigDecimal currentAmount, PricingContext context) {
        return currentAmount.add(context.route().getBaseFee());
    }
}
