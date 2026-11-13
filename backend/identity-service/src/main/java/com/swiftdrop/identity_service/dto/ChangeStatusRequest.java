package com.swiftdrop.identity_service.dto;

import com.swiftdrop.identity_service.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull UserStatus status) {}
