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

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ParcelEventOutbox(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UUID enqueueStatusChanged(Parcel parcel, ParcelStatus oldStatus,
                                     ParcelStatus newStatus, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
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
        try {
            jdbc.update("""
                INSERT INTO parcel_event_outbox
                    (event_id, parcel_id, routing_key, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """, eventId, parcel.getId(), ROUTING_KEY,
                objectMapper.writeValueAsString(event), Timestamp.from(occurredAt));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize parcel status event", exception);
        }
        return eventId;
    }
}
