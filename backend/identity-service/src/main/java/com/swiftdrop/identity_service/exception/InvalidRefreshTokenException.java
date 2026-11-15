package com.swiftdrop.identity_service.exception;

// Deliberately vague: unknown, used, revoked and expired tokens look the same.
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Session has expired. Please sign in again");
    }
}
