package com.swiftdrop.merchant.controller;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Return approval dependency errors directly rather than dispatching to the JWT-protected /error path. */
@RestControllerAdvice(assignableTypes = MerchantController.class)
public class MerchantControllerErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", exception.getReason() == null ? "Merchant request failed" : exception.getReason()));
    }
}
