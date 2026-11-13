
package com.swiftdrop.identity_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail(
            DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidLoginCodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidLoginCode(
            InvalidLoginCodeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<Map<String, String>> handleInactiveAccount(
            AccountNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Invalid request data"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseConflict(
            DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "A conflicting record already exists"));
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCurrentPassword(
            InvalidCurrentPasswordException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<Map<String, String>> handlePasswordMismatch(
            PasswordMismatchException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SamePasswordException.class)
    public ResponseEntity<Map<String, String>> handleSamePassword(
            SamePasswordException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidResetToken(
            InvalidResetTokenException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PasswordResetUnavailableException.class)
    public ResponseEntity<Map<String, String>> handlePasswordResetUnavailable(
            PasswordResetUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({RoleNotAllowedException.class, InvalidStatusChangeException.class})
    public ResponseEntity<Map<String, String>> handleRejectedAdminRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(429)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(
            HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Invalid request data"));
    }
}
