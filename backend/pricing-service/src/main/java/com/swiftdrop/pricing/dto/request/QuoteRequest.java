package com.swiftdrop.pricing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QuoteRequest(

        @NotBlank
        String pickupCity,

        @NotBlank
        String deliveryCity,

        @NotNull
        @DecimalMin("0.001")
        @Digits(integer = 7, fraction = 3)
        BigDecimal weight,

        @NotBlank
        String serviceType

) {
}