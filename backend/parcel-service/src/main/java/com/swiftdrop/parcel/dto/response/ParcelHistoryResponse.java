package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record ParcelHistoryResponse(
    UUID id,
    ParcelStatus previousStatus,
    ParcelStatus newStatus,
    Instant changedAt,
    Long changedBy,
    String note
) {
    public static ParcelHistoryResponse from(ParcelStatusHistory history) {
        return new ParcelHistoryResponse(
            history.getId(),
            history.getPreviousStatus(),
            history.getNewStatus(),
            history.getChangedAt(),
            history.getChangedBy(),
            history.getNote()
        );
    }
}
