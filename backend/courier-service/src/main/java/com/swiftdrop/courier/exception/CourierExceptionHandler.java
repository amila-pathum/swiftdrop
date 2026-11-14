package com.swiftdrop.courier.exception;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(basePackages="com.swiftdrop.courier.controller")
public class CourierExceptionHandler {
    @ExceptionHandler(com.swiftdrop.courier.client.CourierIntegrations.RemoteFailure.class)
    ResponseEntity<?> remote(Exception e) { return ResponseEntity.status(503).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(CourierException.class)
    ResponseEntity<?> domain(CourierException e) { return ResponseEntity.status(e.status).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("error","Invalid request. Check required fields, allowed values, coordinates and timestamps.")); }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<?> conflict(Exception e) { return ResponseEntity.status(409).body(Map.of("error","The record already exists or changed concurrently. Refresh and retry using the same submission ID.")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> upload(Exception e) { return ResponseEntity.status(413).body(Map.of("error","Photos must be at most 5 MB.")); }
}
