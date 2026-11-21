package com.swiftdrop.parcel.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@code parcel.delivery-snapshot.v1} when a courier is assigned.
 * Gives the Courier Service the delivery details its courier needs (addresses,
 * receiver contact, weight and size), which it keeps in its own read model.
 * Field names match the Courier Service consumer (CourierDeliveryEvents.Snapshot).
 */
public record ParcelDeliverySnapshotEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID parcelId,
    String trackingNumber,
    String pickupAddress,
    String deliveryAddress,
    String receiverName,
    String receiverPhone,
    BigDecimal weight,
    String size,
    Long merchantUserId
) { }
