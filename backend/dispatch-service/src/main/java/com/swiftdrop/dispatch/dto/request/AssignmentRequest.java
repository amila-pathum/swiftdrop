package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignmentRequest(@NotNull UUID courierId) {
}
