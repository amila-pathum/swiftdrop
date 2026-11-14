package com.swiftdrop.parcel.event;

import java.time.Instant;
import java.util.UUID;

public record ParcelStatusChangedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String trackingNumber,
    String parcelId,
    String merchantId,
    String merchantOrderReference,
    String customerId,
    String courierId,
    String oldStatus,
    String newStatus
) { }
