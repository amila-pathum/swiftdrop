package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.ParcelStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PublicTrackingResponse(String trackingNumber, ParcelStatus status, Instant createdAt,
        LocalDate estimatedDeliveryDate, String origin, String destination, List<Entry> trackingHistory) {
    public record Entry(ParcelStatus status, String description, String location, Instant timestamp) {}
}
