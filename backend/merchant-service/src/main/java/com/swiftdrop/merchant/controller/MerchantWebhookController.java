package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.request.UpdateMerchantWebhookRequest;
import com.swiftdrop.merchant.dto.response.MerchantWebhookResponse;
import com.swiftdrop.merchant.dto.response.MerchantWebhookWithSecretResponse;
import com.swiftdrop.merchant.service.MerchantWebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/merchants/me/webhook")
public class MerchantWebhookController {
    private final MerchantWebhookService service;

    public MerchantWebhookController(MerchantWebhookService service) {
        this.service = service;
    }

    @GetMapping
    public MerchantWebhookResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(authorizedUserId(jwt));
    }

    @PutMapping
    public MerchantWebhookWithSecretResponse configure(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMerchantWebhookRequest request) {
        return service.configure(authorizedUserId(jwt), request);
    }

    @PostMapping("/rotate-secret")
    public MerchantWebhookWithSecretResponse rotate(@AuthenticationPrincipal Jwt jwt) {
        return service.rotateSecret(authorizedUserId(jwt));
    }

    private Long authorizedUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!"MERCHANT".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MERCHANT role required");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "JWT subject must be a numeric user ID");
        }
    }
}
