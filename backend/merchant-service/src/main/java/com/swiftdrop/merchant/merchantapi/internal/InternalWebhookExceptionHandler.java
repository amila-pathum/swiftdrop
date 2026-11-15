package com.swiftdrop.merchant.merchantapi.internal;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Resolve errors here so servlet error dispatch cannot replace them with a JWT 401. */
@RestControllerAdvice(assignableTypes = InternalWebhookController.class)
public class InternalWebhookExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", exception.getStatusCode().value() == 404
                        ? "MERCHANT_NOT_FOUND" : "SETTINGS_UNAVAILABLE"));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, String>> unavailable(RuntimeException exception) {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(Map.of("error", "SETTINGS_UNAVAILABLE"));
    }
}
