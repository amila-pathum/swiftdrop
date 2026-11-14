package com.swiftdrop.parcel.dto.request;

import com.swiftdrop.parcel.entity.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateParcelStatusRequest(
        @NotNull ParcelStatus status,
        @Size(max = 1000) String note) {}
