package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** {@code courierId} is the courier-service profile id. */
public record AssignmentRequest(@NotNull UUID parcelId, @NotNull @Positive Long courierId) {
}
