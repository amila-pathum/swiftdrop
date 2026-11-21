package com.swiftdrop.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by dispatch-service on routing key parcel.assigned.
 * See contracts/events/parcel.assigned.v1.schema.json. courierUserId is the
 * courier's identity user id, which is also the id notifications are stored under.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParcelAssignedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String parcelId,
        String trackingNumber,
        String courierId,
        String courierUserId,
        String previousCourierId,
        String previousCourierUserId,
        String assignedBy
) {
}
