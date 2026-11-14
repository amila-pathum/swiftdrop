package com.swiftdrop.pricing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QuoteRequest(

        @NotBlank(message = "Pickup city is required")
        String pickupCity,

        @NotBlank(message = "Delivery city is required")
        String deliveryCity,

        @NotNull(message = "Weight is required")
        @DecimalMin(value = "0.001", message = "Weight must be greater than zero (minimum 0.001 kg)")
        @Digits(integer = 7, fraction = 3, message = "Weight can have at most 7 digits and 3 decimal places")
        BigDecimal weight,

        @NotBlank(message = "Service type is required")
        String serviceType,

        @NotBlank(message = "Size is required")
        String size

) {
}
