package com.swiftdrop.pricing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body for creating or updating a zone. */
public record ZoneRequest(

        @NotBlank(message = "Zone name is required")
        @Size(max = 100, message = "Zone name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Zone code is required")
        @Pattern(regexp = "\\s*[A-Za-z0-9]{1,10}\\s*",
                message = "Zone code must be 1 to 10 letters or digits")
        String code,

        @NotEmpty(message = "Add at least one covered locality")
        @Size(max = 200, message = "A zone can cover at most 200 localities")
        List<@NotNull(message = "Localities cannot be empty")
             @Size(max = 100, message = "Each locality must not exceed 100 characters") String> localities,

        Boolean active

) {
}
