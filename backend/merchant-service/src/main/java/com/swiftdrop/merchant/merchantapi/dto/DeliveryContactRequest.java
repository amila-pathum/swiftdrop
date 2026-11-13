package com.swiftdrop.merchant.merchantapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryContactRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotBlank
        @Size(max = 30)
        String phone,

        @NotBlank
        @Size(max = 500)
        String address,

        @NotBlank
        @Size(max = 100)
        String city,

        @Size(max = 20)
        String postalCode

) {
}