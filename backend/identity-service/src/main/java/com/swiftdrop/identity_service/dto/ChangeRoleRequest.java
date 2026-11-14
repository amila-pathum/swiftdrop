package com.swiftdrop.identity_service.dto;

import com.swiftdrop.identity_service.entity.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {
}

