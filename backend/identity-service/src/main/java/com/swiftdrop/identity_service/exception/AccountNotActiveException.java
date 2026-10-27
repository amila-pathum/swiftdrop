package com.swiftdrop.identity_service.exception;

public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException() {
        super("This account is not permitted to sign in");
    }
}
