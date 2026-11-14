package com.swiftdrop.notification.webhook.portal;

import com.swiftdrop.notification.webhook.retry.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Private API: Merchant Service resolves the user to an approved merchant before calling. */
@RestController
@RequestMapping("/internal/webhooks/merchants/{merchantId}/deliveries")
public class WebhookManagementController {
    private final WebhookDeliveryStore store;
    private final MerchantWebhookSettingsLookup settings;
    private final ObjectMapper mapper;
    public WebhookManagementController(WebhookDeliveryStore store,
            MerchantWebhookSettingsLookup settings, ObjectMapper mapper) {
        this.store = store; this.settings = settings; this.mapper = mapper;
    }
    @GetMapping
    public WebhookDeliveryStore.Page history(@PathVariable String merchantId,
            @RequestParam(defaultValue="false") boolean failedOnly,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return store.history(merchantId, failedOnly, page, size);
    }
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String merchantId, @PathVariable UUID id) {
        return Map.of("delivery", store.detail(merchantId, id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found")),
                "attempts", store.attempts(merchantId, id));
    }
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> retry(@PathVariable String merchantId, @PathVariable UUID id) {
        if (store.detail(merchantId, id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found");
        }
        if (!store.retry(merchantId, id, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed deliveries can be retried");
        }
        return Map.of("id", id, "state", "PENDING");
    }
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> test(@PathVariable String merchantId) {
        if (settings.findForMerchant(merchantId).filter(MerchantWebhookSettingsLookup.Settings::enabled).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enable webhook settings before sending a test");
        }
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        byte[] payload = mapper.writeValueAsBytes(Map.of("eventId", eventId.toString(),
                "eventType", "WebhookTest", "occurredAt", now.toString(),
                "trackingNumber", "TEST", "newStatus", "TEST"));
        UUID id = store.enqueue(eventId, merchantId, "TEST", null, "TEST", payload, now);
        return Map.of("id", id, "eventId", eventId, "state", "PENDING");
    }
}
