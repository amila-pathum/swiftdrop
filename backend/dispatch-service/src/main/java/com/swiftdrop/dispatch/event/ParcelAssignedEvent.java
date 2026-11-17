package com.swiftdrop.dispatch.event;

import java.time.Instant;
import java.util.UUID;

public record ParcelAssignedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String parcelId,
    String trackingNumber,
    String courierId,
    String previousCourierId,
    String assignedBy,
    ParcelSnapshot parcel
) {
    public record ParcelSnapshot(String pickupAddress,String deliveryAddress,String receiverName,
        String receiverPhone,java.math.BigDecimal weight,String size) {}
    public ParcelAssignedEvent(UUID eventId,String eventType,Instant occurredAt,String parcelId,String trackingNumber,
            String courierId,String previousCourierId,String assignedBy) {
        this(eventId,eventType,occurredAt,parcelId,trackingNumber,courierId,previousCourierId,assignedBy,null);
    }
}
