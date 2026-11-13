package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMerchantWebhookRequest(
        @NotBlank @Size(max = 2048) String url,
        boolean enabled
) {}
