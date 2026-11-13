package com.swiftdrop.merchant.merchantapi.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(
        basePackages = "com.swiftdrop.merchant.merchantapi.controller"
)
public class MerchantApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidOrder(
            MethodArgumentNotValidException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid order details"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidJson(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid JSON request"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> serviceError(
            ResponseStatusException exception
    ) {
        String message = exception.getReason() == null
                ? "Request could not be completed"
                : exception.getReason();

        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpectedError(
            Exception exception
    ) {
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Order request could not be completed"));
    }
}