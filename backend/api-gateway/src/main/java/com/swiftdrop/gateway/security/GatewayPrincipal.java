package com.swiftdrop.gateway.security;

/**
 * The caller described by a verified access token.
 *
 * @param userId the token subject (Identity Service user id)
 * @param role   one of CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN
 * @param status the account status the token was issued with (ACTIVE,
 *               PENDING_APPROVAL, ...); {@code null} for tokens issued before the
 *               claim existed, which services treat as ACTIVE
 */
public record GatewayPrincipal(String userId, String role, String status) {

    /** Mirrors the services: a role counts only while the account is active. */
    public boolean isActive() {
        return status == null || "ACTIVE".equals(status);
    }
}
