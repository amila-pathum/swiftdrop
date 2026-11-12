package com.swiftdrop.identity_service.exception;

public class SamePasswordException extends RuntimeException {
    public SamePasswordException() {
        super("New password cannot be the same as current password");
    }

    public SamePasswordException(String message) {
        super(message);
    }
}

