package com.swiftdrop.identity_service.exception;

public class PasswordMismatchException extends RuntimeException {
    public PasswordMismatchException() {
        super("New password and confirm password do not match");
    }

    public PasswordMismatchException(String message) {
        super(message);
    }
}

