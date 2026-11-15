package com.swiftdrop.dispatch.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on routing key parcel.assigned after an assignment or reassignment
 * commits. See contracts/events/parcel.assigned.v1.schema.json.
 * {@code courierId} is the courier-service profile id; {@code courierUserId} the
 * courier's identity user id.
 */
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
) { }
