package com.swiftdrop.identity_service.dto;

import java.time.Instant;

/**
 * A freshly issued reset token. It is returned exactly once and cannot be
 * recovered later; {@code resetUrl} is the frontend page that consumes it.
 */
public record PasswordResetTokenResponse(String token, Instant expiresAt, String resetUrl) {}
