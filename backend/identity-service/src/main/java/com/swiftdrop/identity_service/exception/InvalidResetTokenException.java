package com.swiftdrop.identity_service.exception;

// Deliberately vague: unknown, used and expired tokens look the same.
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("Invalid or expired password reset token");
    }
}
