package com.swiftdrop.merchant.merchantapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMerchantOrderRequest(

        @NotBlank
        @Size(max = 100)
        String merchantOrderReference,

        @NotNull
        @Valid
        DeliveryContactRequest pickup,

        @NotNull
        @Valid
        DeliveryContactRequest receiver,

        @NotNull
        @Valid
        ParcelDetailsRequest parcel

) {
}