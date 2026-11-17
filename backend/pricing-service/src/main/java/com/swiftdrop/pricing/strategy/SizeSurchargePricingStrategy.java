package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The published surcharge for the parcel's size band. */
@Order(3)
@Component
public class SizeSurchargePricingStrategy implements PricingStrategy {

    @Override
    public String breakdownKey() {
        return "sizeFee";
    }

    @Override
    public BigDecimal calculate(BigDecimal currentAmount, PricingContext context) {
        return currentAmount.add(context.rates().surchargeFor(context.size()));
    }
}
