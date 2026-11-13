package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.*;

public record MerchantRegistrationRequest(
    @NotBlank String businessName,
    @NotBlank String registrationNumber,
    @NotBlank String contactName,
    @NotBlank @Email String email,
    @NotBlank String phone,
    @NotBlank String address
) {}