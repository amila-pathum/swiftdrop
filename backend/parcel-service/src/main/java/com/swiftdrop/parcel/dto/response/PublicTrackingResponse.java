package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.ParcelStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Explicit public allowlist; never serialize entities or internal history notes. */
public record PublicTrackingResponse(String trackingNumber, ParcelStatus status,
        Instant createdAt, LocalDate estimatedDeliveryDate, String origin, String destination,
        List<TrackingEntry> trackingHistory) {
    public record TrackingEntry(ParcelStatus status, String description, String location,
                                Instant timestamp) { }
}
