package com.swiftdrop.parcel.merchantintegration;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=MerchantIntegrationController.class)
public class MerchantIntegrationErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", exception.getReason() == null ? "Order request failed" : exception.getReason()));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid order details"));
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String,String>> forbidden(Exception exception) {
        return ResponseEntity.status(403).body(Map.of("error", "Merchant authentication is required"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> failed(Exception exception) {
        return ResponseEntity.status(503).body(Map.of("error", "Parcel creation is unavailable"));
    }
}
