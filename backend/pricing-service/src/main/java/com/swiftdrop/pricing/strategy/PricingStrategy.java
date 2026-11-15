package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;

/**
 * One step of a price. The {@link PricingCalculator} runs every strategy in
 * {@code @Order}, passing each the running amount, and records how much each step
 * changed it under {@link #breakdownKey()}. A new pricing rule is a new strategy
 * class; existing ones do not change.
 */
public interface PricingStrategy {

    /** The name this step's amount appears under in the quote breakdown. */
    String breakdownKey();

    BigDecimal calculate(BigDecimal currentAmount, PricingContext context);
}
