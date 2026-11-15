package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMerchantWebhookRequest(
        @NotBlank @Size(max = 2048) String url,
        boolean enabled,
        @Size(max = 7) java.util.Set<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(
                regexp = "CREATED|PICKED_UP|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|FAILED|CANCELLED") String> events
) {
    public UpdateMerchantWebhookRequest(String url, boolean enabled) { this(url, enabled, null); }
}
