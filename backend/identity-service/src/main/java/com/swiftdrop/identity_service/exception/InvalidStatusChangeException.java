package com.swiftdrop.identity_service.exception;

// e.g. an administrator changing their own status, or a status the role cannot have.
public class InvalidStatusChangeException extends RuntimeException {
    public InvalidStatusChangeException() {
        super("This status change is not allowed");
    }

    public InvalidStatusChangeException(String message) {
        super(message);
    }
}
