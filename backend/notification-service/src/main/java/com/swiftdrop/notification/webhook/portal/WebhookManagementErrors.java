package com.swiftdrop.notification.webhook.portal;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = WebhookManagementController.class)
public class WebhookManagementErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
    }
    @ExceptionHandler({IllegalArgumentException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unavailable(Exception e) {
        return ResponseEntity.status(503).body(Map.of("error", "Webhook management is unavailable"));
    }
}
