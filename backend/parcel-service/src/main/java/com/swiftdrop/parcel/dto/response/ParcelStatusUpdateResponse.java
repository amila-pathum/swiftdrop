package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record ParcelStatusUpdateResponse(UUID parcelId, ParcelStatus previousStatus,
                                         ParcelStatus newStatus, Instant changedAt) {}
