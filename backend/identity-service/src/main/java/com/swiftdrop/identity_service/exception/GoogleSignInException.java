package com.swiftdrop.identity_service.exception;

/**
 * Google sign-in was refused. The reason is reported to the frontend as a short
 * code, never with account details.
 */
public class GoogleSignInException extends RuntimeException {

    public enum Reason {
        EMAIL_UNVERIFIED("email_unverified"),
        // A SwiftDrop account already uses this email; it is not linked automatically.
        ACCOUNT_EXISTS("account_exists"),
        ACCOUNT_INACTIVE("account_inactive"),
        INVALID_PROFILE("google_failed");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Reason reason;

    public GoogleSignInException(Reason reason) {
        super("Google sign-in refused: " + reason.code());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
