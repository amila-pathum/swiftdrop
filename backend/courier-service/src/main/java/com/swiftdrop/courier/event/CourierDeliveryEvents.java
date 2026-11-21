package com.swiftdrop.courier.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.courier.config.CourierRabbitConfig;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Owner events are projected only into courier_db; no cross-database queries. */
@Service
public class CourierDeliveryEvents {
    private static final Set<String> STATUSES = Set.of("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CourierDeliveryEvents(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Assigned(UUID eventId, String eventType, Instant occurredAt, UUID parcelId,
        String trackingNumber, String courierId, String previousCourierId, String assignedBy,
        UUID assignmentId, LocalDate deliveryDate, Integer attemptNo, Long version, AssignedParcel parcel, String courierUserId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssignedParcel(String pickupAddress,String deliveryAddress,String receiverName,
        String receiverPhone,BigDecimal weight,String size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(UUID eventId, String eventType, Instant occurredAt, UUID parcelId,
        String trackingNumber, String oldStatus, String newStatus,String locationDescription,Integer attemptNo,String actorRole,String merchantId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record History(UUID id,String status,Instant recordedAt,String locationDescription,Integer attemptNo,String actorRole) {}

    /** Optional snapshot handoff. Existing Parcel outbox can carry demo snapshots unchanged. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snapshot(UUID eventId, String eventType, Instant occurredAt, UUID parcelId,
        String trackingNumber, String pickupAddress, String deliveryAddress, String receiverName,
        String receiverPhone, BigDecimal weight, String size, LocalDate deliveryDate,
        String pickupContactName,String pickupContactPhone,String contentsDescription,
        BigDecimal lengthCm,BigDecimal widthCm,BigDecimal heightCm,java.util.List<History> history,Long merchantUserId) {}

    @RabbitListener(queues = CourierRabbitConfig.QUEUE)
    @Transactional
    public void receive(Message message) {
        String route = message.getMessageProperties().getReceivedRoutingKey();
        switch (route) {
            case CourierRabbitConfig.ASSIGNED -> assigned(mapper.readValue(message.getBody(), Assigned.class));
            case CourierRabbitConfig.STATUS -> status(mapper.readValue(message.getBody(), Status.class));
            case CourierRabbitConfig.SNAPSHOT -> snapshot(mapper.readValue(message.getBody(), Snapshot.class));
            default -> throw new IllegalArgumentException("Unsupported courier event route");
        }
    }

    private boolean begin(UUID eventId, String type, String expected, Instant at, UUID parcelId, String tracking, String route) {
        if (eventId == null || !expected.equals(type) || at == null || parcelId == null
                || tracking == null || tracking.isBlank() || tracking.length() > 40) {
            throw new IllegalArgumentException("Invalid courier delivery event envelope");
        }
        if (jdbc.update("INSERT INTO courier_delivery_inbox(event_id,routing_key,occurred_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                eventId, route, Timestamp.from(at)) == 0) return false;
        jdbc.update("INSERT INTO courier_delivery_parcels(parcel_id,tracking_number) VALUES (?,?) ON CONFLICT DO NOTHING", parcelId, tracking);
        // Serialize all event streams for a parcel, including concurrent first deliveries.
        jdbc.queryForObject("SELECT parcel_id FROM courier_delivery_parcels WHERE parcel_id=? FOR UPDATE", UUID.class, parcelId);
        return true;
    }

    private void assigned(Assigned event) {
        if (event.courierId() == null || event.attemptNo() != null && event.attemptNo() < 1
                || event.version() != null && event.version() < 0) throw new IllegalArgumentException("Invalid assignment event");
        if (!begin(event.eventId(), event.eventType(), "ParcelAssigned", event.occurredAt(), event.parcelId(), event.trackingNumber(), CourierRabbitConfig.ASSIGNED)) return;
        UUID courierReference=resolveCourier(event.courierId(),event.courierUserId());
        jdbc.update("""
            INSERT INTO courier_delivery_assignments(parcel_id,projection_id,dispatch_assignment_id,dispatch_courier_id,
                assigned_at,delivery_date,attempt_no,assignment_version) VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT (parcel_id) DO UPDATE SET projection_id=excluded.projection_id,
                dispatch_assignment_id=excluded.dispatch_assignment_id,dispatch_courier_id=excluded.dispatch_courier_id,
                assigned_at=excluded.assigned_at,delivery_date=excluded.delivery_date,
                attempt_no=CASE WHEN CAST(? AS BIGINT) IS NOT NULL THEN excluded.attempt_no
                    WHEN (SELECT status FROM courier_delivery_parcels WHERE parcel_id=excluded.parcel_id)='FAILED'
                    THEN courier_delivery_assignments.attempt_no+1 ELSE courier_delivery_assignments.attempt_no END,
                assignment_version=CASE WHEN CAST(? AS BIGINT) IS NOT NULL THEN excluded.assignment_version
                    ELSE courier_delivery_assignments.assignment_version+1 END,
                managed_locally=false,active=true
            WHERE courier_delivery_assignments.assigned_at < excluded.assigned_at
            """, event.parcelId(), event.eventId(), event.assignmentId(), courierReference, Timestamp.from(event.occurredAt()),
            event.deliveryDate(), event.attemptNo() == null ? 1 : event.attemptNo(), event.version() == null ? 0 : event.version(), event.attemptNo(),event.version());
        // Dispatch currently accepts assignment only while Parcel is CREATED.
        // Preserve any later canonical Parcel status that has already arrived.
        jdbc.update("""
            UPDATE courier_delivery_parcels SET status='CREATED',status_at=?
            WHERE parcel_id=? AND (status_at IS NULL OR status_at < ?)
            """, Timestamp.from(event.occurredAt()), event.parcelId(), Timestamp.from(event.occurredAt()));
        if (event.parcel()!=null) {
            var p=event.parcel();
            jdbc.update("""
                UPDATE courier_delivery_parcels SET pickup_address=coalesce(?,pickup_address),delivery_address=coalesce(?,delivery_address),
                    receiver_name=coalesce(?,receiver_name),receiver_phone=coalesce(?,receiver_phone),
                    weight=coalesce(?,weight),size=coalesce(?,size),snapshot_at=?
                WHERE parcel_id=? AND (snapshot_at IS NULL OR snapshot_at < ?)
                """,p.pickupAddress(),p.deliveryAddress(),p.receiverName(),p.receiverPhone(),p.weight(),p.size(),
                Timestamp.from(event.occurredAt()),event.parcelId(),Timestamp.from(event.occurredAt()));
        }
    }

    private void status(Status event) {
        if (!STATUSES.contains(event.newStatus() == null ? "" : event.newStatus())
                || ("CREATED".equals(event.newStatus()) ? event.oldStatus() != null
                    : event.oldStatus() == null || event.oldStatus().isBlank())) throw new IllegalArgumentException("Invalid status event");
        if (!begin(event.eventId(), event.eventType(), "ParcelStatusChanged", event.occurredAt(), event.parcelId(), event.trackingNumber(), CourierRabbitConfig.STATUS)) return;
        // Parcel's existing merchantId is the numeric Identity user ID (verified
        // from its creation contract), not a Merchant business-profile ID.
        if(event.merchantId()!=null) {
            try {
                Long merchant=Long.valueOf(event.merchantId());
                if(merchant<=0||!merchant.toString().equals(event.merchantId()))throw new NumberFormatException();
                merchantOwner(event.parcelId(),merchant);
            } catch(NumberFormatException e) {throw new IllegalArgumentException("Invalid merchant identity in Parcel event");}
        }
        saveHistory(event.parcelId(),new History(event.eventId(),event.newStatus(),event.occurredAt(),event.locationDescription(),event.attemptNo(),event.actorRole()));
        jdbc.update("""
            UPDATE courier_delivery_parcels SET status=?,status_at=?
            WHERE parcel_id=? AND (status_at IS NULL OR status_at < ?)
            """, event.newStatus(), Timestamp.from(event.occurredAt()), event.parcelId(), Timestamp.from(event.occurredAt()));
    }

    private void snapshot(Snapshot event) {
        if (event.weight() != null && event.weight().signum() <= 0) throw new IllegalArgumentException("Invalid parcel weight");
        if(event.merchantUserId()!=null&&event.merchantUserId()<=0)throw new IllegalArgumentException("Invalid merchant user ID");
        if (!begin(event.eventId(), event.eventType(), "ParcelDeliverySnapshot", event.occurredAt(), event.parcelId(), event.trackingNumber(), CourierRabbitConfig.SNAPSHOT)) return;
        if(event.merchantUserId()!=null)merchantOwner(event.parcelId(),event.merchantUserId());
        jdbc.update("""
            UPDATE courier_delivery_parcels SET tracking_number=?,pickup_address=?,delivery_address=?,receiver_name=?,
                receiver_phone=?,weight=?,size=?,delivery_date=?,snapshot_at=?,
                pickup_contact_name=coalesce(?,pickup_contact_name),pickup_contact_phone=coalesce(?,pickup_contact_phone),
                contents_description=coalesce(?,contents_description),length_cm=coalesce(?,length_cm),width_cm=coalesce(?,width_cm),height_cm=coalesce(?,height_cm)
            WHERE parcel_id=? AND (snapshot_at IS NULL OR snapshot_at < ?)
            """, event.trackingNumber(), event.pickupAddress(), event.deliveryAddress(), event.receiverName(), event.receiverPhone(),
            event.weight(), event.size(), event.deliveryDate(), Timestamp.from(event.occurredAt()),event.pickupContactName(),event.pickupContactPhone(),
            event.contentsDescription(),event.lengthCm(),event.widthCm(),event.heightCm(),event.parcelId(), Timestamp.from(event.occurredAt()));
        if(event.history()!=null) {
            if(event.history().size()>500)throw new IllegalArgumentException("History snapshot is too large");
            for(var entry:event.history())saveHistory(event.parcelId(),entry);
        }
    }

    /** Current Dispatch sends numeric profile and Identity IDs; UUID-only events are legacy. */
    private UUID resolveCourier(String id,String userId) {
        if(id.matches("[1-9][0-9]*")) {
            if(userId==null||!userId.matches("[1-9][0-9]*"))
                throw new IllegalArgumentException("Dispatch assignment requires a courier Identity user ID");
            try {
                var rows=jdbc.query("SELECT dispatch_courier_id FROM courier_profiles WHERE id=? AND user_id=?",
                    (rs,row)->rs.getObject(1,UUID.class),Long.valueOf(id),Long.valueOf(userId));
                if(rows.isEmpty())throw new IllegalArgumentException("Dispatch courier profile and Identity do not match");
                return rows.getFirst();
            } catch(NumberFormatException e) {throw new IllegalArgumentException("Invalid numeric courier identity");}
        }
        if(userId!=null)throw new IllegalArgumentException("Invalid Dispatch courier profile ID");
        try {return UUID.fromString(id);}
        catch(IllegalArgumentException e) {throw new IllegalArgumentException("Invalid legacy courier reference");}
    }
    private void merchantOwner(UUID parcelId,Long merchant) {
        var current=jdbc.queryForObject("SELECT merchant_user_id FROM courier_delivery_parcels WHERE parcel_id=?",Long.class,parcelId);
        if(current!=null&&!current.equals(merchant))throw new IllegalArgumentException("Conflicting merchant user identity");
        jdbc.update("UPDATE courier_delivery_parcels SET merchant_user_id=coalesce(merchant_user_id,?) WHERE parcel_id=?",merchant,parcelId);
    }

    private void saveHistory(UUID parcelId,History entry) {
        if(entry==null||entry.id()==null||entry.recordedAt()==null||!STATUSES.contains(entry.status()==null?"":entry.status())
                ||entry.attemptNo()!=null&&entry.attemptNo()<1)throw new IllegalArgumentException("Invalid parcel history");
        jdbc.update("""
            INSERT INTO courier_delivery_history(id,parcel_id,status,recorded_at,location_description,attempt_no,actor_role)
            VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
                location_description=coalesce(courier_delivery_history.location_description,excluded.location_description),
                attempt_no=coalesce(courier_delivery_history.attempt_no,excluded.attempt_no),
                actor_role=coalesce(courier_delivery_history.actor_role,excluded.actor_role)
            WHERE courier_delivery_history.parcel_id=excluded.parcel_id
              AND courier_delivery_history.status=excluded.status AND courier_delivery_history.recorded_at=excluded.recorded_at
            """,entry.id(),parcelId,entry.status(),Timestamp.from(entry.recordedAt()),entry.locationDescription(),entry.attemptNo(),entry.actorRole());
    }
}
