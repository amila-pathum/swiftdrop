package com.swiftdrop.identity_service.dto;

import com.swiftdrop.identity_service.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An administrator creating a DISPATCHER or ADMIN account. */
public record CreateStaffRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 255) String email,
    // Staff accounts are privileged, so their passwords are longer than customers'.
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotNull Role role
) {}
