package com.swiftdrop.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank
    String currentPassword,

    @NotBlank
    @Size(min = 8, max = 72)
    String newPassword,

    String confirmPassword,
    String confirmNewPassword
) {
    @Override
    public String confirmPassword() {
        return confirmPassword != null ? confirmPassword : confirmNewPassword;
    }

    @Override
    public String confirmNewPassword() {
        return confirmNewPassword != null ? confirmNewPassword : confirmPassword;
    }
}

