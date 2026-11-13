package com.swiftdrop.dispatch.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestControllerAdvice
public class DispatchApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(DispatchApiExceptionHandler.class);

    @ExceptionHandler(DispatchException.class)
    ResponseEntity<Map<String, String>> handleDispatch(DispatchException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
        ConstraintViolationException.class})
    ResponseEntity<Map<String, String>> handleInvalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request parameters"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> handleDatabaseUnavailable(DataAccessException exception) {
        log.error("Dispatch database operation failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Dispatch data is temporarily unavailable"));
    }
}
