package com.swiftdrop.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// rememberMe is optional; true selects the longer refresh token lifetime.
public record LoginRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 72) String password,
    Boolean rememberMe
) {
    public boolean persistent() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
