package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParcelResponse(UUID id, String trackingNumber,
                             ParcelStatus status, BigDecimal charge, Instant createdAt) {
    public static ParcelResponse from(Parcel p) {
        return new ParcelResponse(p.getId(), p.getTrackingNumber(), p.getStatus(),
                                  p.getCharge(), p.getCreatedAt());
    }
}
