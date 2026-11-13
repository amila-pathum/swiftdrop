package com.swiftdrop.pricing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateZoneRequest(

        @NotBlank(message = "Zone name is required")
        @Size(max = 100, message = "Zone name must not exceed 100 characters")
        String name,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "District is required")
        @Size(max = 100, message = "District must not exceed 100 characters")
        String district

) {
}