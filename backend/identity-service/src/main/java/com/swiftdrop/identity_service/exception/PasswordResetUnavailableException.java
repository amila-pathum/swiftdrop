package com.swiftdrop.identity_service.exception;

// No email delivery is configured, so no reset link can be sent.
public class PasswordResetUnavailableException extends RuntimeException {
    public PasswordResetUnavailableException() {
        super("Password reset by email is not available yet. Ask a SwiftDrop administrator to reset your password.");
    }
}
