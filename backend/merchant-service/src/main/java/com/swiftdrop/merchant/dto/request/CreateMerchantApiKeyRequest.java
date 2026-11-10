package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMerchantApiKeyRequest(
        @NotBlank @Size(max = 100) String name
) {}
