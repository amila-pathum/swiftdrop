package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignCourierRequest(@NotNull @Positive Long courierUserId) {}
