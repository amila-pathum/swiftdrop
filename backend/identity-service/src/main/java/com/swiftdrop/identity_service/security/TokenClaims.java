package com.swiftdrop.identity_service.security;

import com.swiftdrop.identity_service.entity.UserStatus;

/**
 * Everything a verified access token says about its user.
 *
 * @param status       the account status when the token was issued; null for
 *                     tokens issued before the claim existed (always ACTIVE accounts)
 * @param tokenVersion the user's token version when the token was issued
 */
public record TokenClaims(AuthenticatedUser principal, UserStatus status, int tokenVersion) {}
