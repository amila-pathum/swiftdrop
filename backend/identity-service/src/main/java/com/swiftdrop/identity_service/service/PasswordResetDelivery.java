package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.entity.User;

import java.time.Instant;

/**
 * Delivers a password reset link to its user, e.g. by email.
 *
 * <p>No implementation exists yet: SwiftDrop has no email integration. Until a
 * bean of this type is registered, {@code POST /api/auth/password-reset/request}
 * answers 501 instead of claiming that an email was sent, and reset tokens are
 * issued by an administrator ({@code POST /api/admin/users/{id}/password-reset}).
 */
public interface PasswordResetDelivery {

    void send(User user, String resetUrl, Instant expiresAt);
}
