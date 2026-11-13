package com.swiftdrop.pricing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePricingRuleRequest(

        @NotNull(message = "Zone ID is required")
        UUID zoneId,

        @NotNull(message = "Base fee is required")
        @DecimalMin(value = "0.00", message = "Base fee cannot be negative")
        @Digits(integer = 10, fraction = 2)
        BigDecimal baseFee,

        @NotNull(message = "Price per kg is required")
        @DecimalMin(value = "0.00", message = "Price per kg cannot be negative")
        @Digits(integer = 10, fraction = 2)
        BigDecimal pricePerKg,

        @NotNull(message = "Express multiplier is required")
        @DecimalMin(value = "1.00", message = "Express multiplier must be at least 1.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal expressMultiplier

) {
}