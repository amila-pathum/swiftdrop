package com.swiftdrop.identity_service.entity;

/**
 * Identity account status. This is about the account itself (may it sign in,
 * may it act), not about a merchant's or courier's business application, which
 * the Merchant and Courier Services own.
 */
public enum UserStatus {
    /** Fully usable. */
    ACTIVE,
    /**
     * A merchant or courier account that an administrator has not activated yet.
     * It may sign in and read its own profile, but holds no role authority, so
     * every role-restricted operation is refused.
     */
    PENDING_APPROVAL,
    SUSPENDED,
    DISABLED;

    /** Whether the account may authenticate at all. */
    public boolean canSignIn() {
        return this == ACTIVE || this == PENDING_APPROVAL;
    }
}
