package com.swiftdrop.identity_service.exception;

public class InvalidRoleChangeException extends RuntimeException {

    public InvalidRoleChangeException() {
        super("This role change is not allowed");
    }

    public InvalidRoleChangeException(String message) {
        super(message);
    }
}

