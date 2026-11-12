package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectMerchantRequest(
        @NotBlank(message = "Rejection reason is required")
        String reason
) {}
