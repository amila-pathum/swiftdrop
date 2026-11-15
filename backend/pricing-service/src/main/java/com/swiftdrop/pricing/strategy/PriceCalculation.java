package com.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import java.util.Map;

/** The total and what each strategy added; the breakdown values sum to the amount. */
public record PriceCalculation(BigDecimal amount, Map<String, BigDecimal> breakdown) {
}
