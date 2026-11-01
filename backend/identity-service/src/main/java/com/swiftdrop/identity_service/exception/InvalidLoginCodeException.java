package com.swiftdrop.identity_service.exception;

// Deliberately vague: unknown, used, expired and mismatched codes look the same.
public class InvalidLoginCodeException extends RuntimeException {
    public InvalidLoginCodeException() {
        super("Invalid or expired sign-in code");
    }
}
