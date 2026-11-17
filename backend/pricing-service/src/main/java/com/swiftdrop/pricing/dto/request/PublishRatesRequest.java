package com.swiftdrop.pricing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Publishes a new rate version. {@code basedOnVersion} is the version the admin was
 * editing (null when none was published yet); publishing fails if another admin has
 * published since. Pairs that are left out cannot be booked in the new version.
 */
public record PublishRatesRequest(

        Integer basedOnVersion,

        @NotNull(message = "Size surcharges are required")
        @Valid
        SizeSurcharges sizeSurcharges,

        @NotNull(message = "Routes are required")
        @Size(max = 5000, message = "Too many routes")
        List<@NotNull @Valid Route> routes

) {
    public record SizeSurcharges(
            @NotNull(message = "Small surcharge is required")
            @DecimalMin(value = "0.00", message = "Surcharge cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal small,

            @NotNull(message = "Medium surcharge is required")
            @DecimalMin(value = "0.00", message = "Surcharge cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal medium,

            @NotNull(message = "Large surcharge is required")
            @DecimalMin(value = "0.00", message = "Surcharge cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal large
    ) {
    }

    public record Route(
            @NotNull(message = "Origin zone is required")
            UUID originZoneId,

            @NotNull(message = "Destination zone is required")
            UUID destinationZoneId,

            @NotNull(message = "Base weight is required")
            @DecimalMin(value = "0.000", message = "Base weight cannot be negative")
            @Digits(integer = 7, fraction = 3, message = "Use at most 3 decimal places")
            BigDecimal baseWeightKg,

            @NotNull(message = "Base fee is required")
            @DecimalMin(value = "0.00", message = "Base fee cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal baseFee,

            @NotNull(message = "Additional kg fee is required")
            @DecimalMin(value = "0.00", message = "Additional kg fee cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal additionalKgFee,

            @NotNull(message = "Express multiplier is required")
            @DecimalMin(value = "1.00", message = "Express multiplier must be at least 1.00")
            @Digits(integer = 3, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal expressMultiplier
    ) {
    }
}
