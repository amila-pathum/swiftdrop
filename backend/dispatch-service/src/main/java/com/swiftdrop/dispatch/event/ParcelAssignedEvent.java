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
    String assignedBy
) { }
