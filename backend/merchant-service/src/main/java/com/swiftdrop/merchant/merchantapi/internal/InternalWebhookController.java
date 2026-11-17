package com.swiftdrop.merchant.merchantapi.internal;

import com.swiftdrop.merchant.service.MerchantWebhookService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/merchants")
public class InternalWebhookController {

    private final MerchantWebhookService service;

    public InternalWebhookController(MerchantWebhookService service) {
        this.service = service;
    }

    @GetMapping("/{merchantId}/webhook")
    public ResponseEntity<MerchantWebhookService.InternalWebhookSettings> get(
            @PathVariable Long merchantId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getInternalSettings(merchantId));
    }

    /** Parcel events carry Identity user IDs, never Merchant profile IDs. */
    @GetMapping("/by-user/{userId}/profile")
    public ResponseEntity<java.util.Map<String, Long>> profile(@PathVariable Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("merchantId", service.profileIdForUser(userId)));
    }
}
