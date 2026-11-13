package com.swiftdrop.merchant.merchantapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ParcelDetailsRequest(

        @NotBlank
        @Size(max = 500)
        String description,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal weightKg,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal lengthCm,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal widthCm,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal heightCm,

        @Size(max = 1000)
        String handlingNote

) {
}