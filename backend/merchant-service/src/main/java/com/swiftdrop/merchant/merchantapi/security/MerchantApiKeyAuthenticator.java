package com.swiftdrop.merchant.merchantapi.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MerchantApiKeyAuthenticator {

    private final MerchantApiKeyLookup keyLookup;

    public MerchantApiKeyAuthenticator(
            MerchantApiKeyLookup keyLookup
    ) {
        this.keyLookup = keyLookup;
    }

    public MerchantKeyDetails authenticate(String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw unauthorized();
        }

        MerchantKeyDetails merchant = keyLookup
                .findByApiKey(apiKey)
                .orElseThrow(this::unauthorized);

        if (!merchant.approved()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Merchant account is not approved"
            );
        }

        return merchant;
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing or invalid API key"
        );
    }
}