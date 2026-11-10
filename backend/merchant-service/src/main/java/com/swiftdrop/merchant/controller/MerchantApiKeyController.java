package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.request.CreateMerchantApiKeyRequest;
import com.swiftdrop.merchant.dto.response.CreatedMerchantApiKeyResponse;
import com.swiftdrop.merchant.dto.response.MerchantApiKeyResponse;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/merchants/me/api-keys")
public class MerchantApiKeyController {
    private final MerchantApiKeyService service;

    public MerchantApiKeyController(MerchantApiKeyService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreatedMerchantApiKeyResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMerchantApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(authorizedUserId(jwt), request.name()));
    }

    @GetMapping
    public List<MerchantApiKeyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(authorizedUserId(jwt));
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long keyId) {
        service.revoke(authorizedUserId(jwt), keyId);
    }

    private Long authorizedUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String role = jwt.getClaimAsString("role");
        if (!"MERCHANT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "MERCHANT role required");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "JWT subject must be a numeric user ID");
        }
    }
}
