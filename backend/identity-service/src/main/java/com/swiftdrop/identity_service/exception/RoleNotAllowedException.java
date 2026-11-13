package com.swiftdrop.identity_service.exception;

// Customers, merchants and couriers register themselves.
public class RoleNotAllowedException extends RuntimeException {
    public RoleNotAllowedException() {
        super("Only DISPATCHER and ADMIN accounts can be created by an administrator");
    }
}
