package com.swiftdrop.identity_service.entity;

/**
 * The single definition of SwiftDrop roles. The same names are used in the
 * database, in the JWT {@code role} claim and in API responses; other services
 * see them as the authority {@code ROLE_<name>}.
 */
public enum Role {
    CUSTOMER,
    MERCHANT,
    COURIER,
    DISPATCHER,
    ADMIN;

    /**
     * The status a newly created account of this role starts with. Merchants and
     * couriers wait for an administrator; a role is never taken from a request.
     */
    public UserStatus initialStatus() {
        return this == MERCHANT || this == COURIER
            ? UserStatus.PENDING_APPROVAL
            : UserStatus.ACTIVE;
    }

    /** Roles that only an administrator can create; never available to public registration. */
    public boolean isStaff() {
        return this == DISPATCHER || this == ADMIN;
    }
}
