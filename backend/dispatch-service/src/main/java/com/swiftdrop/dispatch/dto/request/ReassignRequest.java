package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code courierId} is the courier-service profile id of the new courier. */
public record ReassignRequest(@NotNull @Positive Long courierId) {
}
