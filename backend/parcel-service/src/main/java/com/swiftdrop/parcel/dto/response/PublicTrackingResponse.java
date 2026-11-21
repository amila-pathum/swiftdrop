package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.ParcelStatus;
import java.time.Instant;
import java.util.List;

/**
 * Public, unauthenticated tracking view. An explicit allowlist: the current
 * status, the status history (status and time only) and the delivery city
 * (district). Never names, phone numbers, addresses, internal ids or history notes.
 */
public record PublicTrackingResponse(String trackingNumber, ParcelStatus status,
        Instant createdAt, String deliveryCity, List<TrackingEntry> trackingHistory) {
    public record TrackingEntry(ParcelStatus status, Instant timestamp) { }
}
