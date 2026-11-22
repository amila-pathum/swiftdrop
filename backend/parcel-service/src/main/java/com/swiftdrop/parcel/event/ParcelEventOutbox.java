package com.swiftdrop.parcel.event;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParcelEventOutbox {
    public static final String ROUTING_KEY = "parcel.status-changed";
    public static final String DELIVERY_SNAPSHOT_ROUTING_KEY = "parcel.delivery-snapshot.v1";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ParcelEventOutbox(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UUID enqueueStatusChanged(Parcel parcel, ParcelStatus oldStatus,
                                     ParcelStatus newStatus, Instant occurredAt) {
        return enqueueStatusChanged(UUID.randomUUID(), parcel, oldStatus, newStatus, occurredAt);
    }

    /**
     * Same as above with a caller-chosen event id. A courier update reuses its
     * submission id, so the Courier Service recognises the event as its own
     * update and does not list it twice.
     */
    public UUID enqueueStatusChanged(UUID eventId, Parcel parcel, ParcelStatus oldStatus,
                                     ParcelStatus newStatus, Instant occurredAt) {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
            eventId,
            "ParcelStatusChanged",
            occurredAt,
            parcel.getTrackingNumber(),
            parcel.getId().toString(),
            parcel.getMerchantId() == null ? null : parcel.getMerchantId().toString(),
            parcel.getMerchantOrderReference(),
            parcel.getSenderId().toString(),
            parcel.getAssignedCourierId() == null ? null : parcel.getAssignedCourierId().toString(),
            oldStatus == null ? null : oldStatus.name(),
            newStatus.name()
        );
        insert(eventId, parcel.getId(), ROUTING_KEY, event, occurredAt);
        return eventId;
    }

    /**
     * Queues the delivery details for the Courier Service. Sent when a courier is
     * assigned, so only parcels a courier works on leave this service.
     */
    public UUID enqueueDeliverySnapshot(Parcel parcel, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        ParcelDeliverySnapshotEvent event = new ParcelDeliverySnapshotEvent(
            eventId,
            "ParcelDeliverySnapshot",
            occurredAt,
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getPickupAddress(),
            parcel.getDeliveryAddress(),
            parcel.getReceiverName(),
            parcel.getReceiverPhone(),
            parcel.getWeight(),
            parcel.getSize(),
            parcel.getMerchantId()
        );
        insert(eventId, parcel.getId(), DELIVERY_SNAPSHOT_ROUTING_KEY, event, occurredAt);
        return eventId;
    }

    private void insert(UUID eventId, UUID parcelId, String routingKey, Object event, Instant occurredAt) {
        try {
            jdbc.update("""
                INSERT INTO parcel_event_outbox
                    (event_id, parcel_id, routing_key, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """, eventId, parcelId, routingKey,
                objectMapper.writeValueAsString(event), Timestamp.from(occurredAt));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize parcel event " + routingKey, exception);
        }
    }
}
