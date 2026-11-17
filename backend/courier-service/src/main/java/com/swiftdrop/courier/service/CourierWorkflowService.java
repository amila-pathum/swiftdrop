package com.swiftdrop.courier.service;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.dto.CourierWorkflowDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.event.CourierOutbox;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dispatch owns assignment; Courier records activity against received assignments. */
@Service
public class CourierWorkflowService {
    private static final Set<String> STATUSES=Set.of("CREATED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","CANCELLED");
    private static final Map<String,Set<String>> NEXT=Map.of("CREATED",Set.of("PICKED_UP"),"PICKED_UP",Set.of("IN_TRANSIT"),
        "IN_TRANSIT",Set.of("OUT_FOR_DELIVERY"),"OUT_FOR_DELIVERY",Set.of("DELIVERED","FAILED"));
    private final JdbcTemplate jdbc;
    private final CourierProfileRepository profiles;
    private final CourierProfileService profileService;
    private final CourierOutbox outbox;

    public CourierWorkflowService(JdbcTemplate jdbc,CourierProfileRepository profiles,CourierProfileService profileService,
            CourierOutbox outbox) {
        this.jdbc=jdbc;this.profiles=profiles;this.profileService=profileService;this.outbox=outbox;
    }
    private record Parcel(Long merchant,String status,LocalDate date,String tracking) {}
    private record Assigned(UUID id,UUID courier,int attempt,long version,Instant at,LocalDate date,boolean active,boolean local) {}

    @Transactional(readOnly=true)
    public PageResult<AvailableCourier> available(String area,VehicleType vehicle,int page,int size) {
        paging(page,size);var args=new ArrayList<Object>();
        String where=" WHERE p.active=true AND p.availability='AVAILABLE'";
        if(area!=null&&!area.isBlank()){where+=" AND lower(p.area)=lower(?)";args.add(area.trim());}
        if(vehicle!=null){where+=" AND p.vehicle_type=?";args.add(vehicle.name());}
        long total=jdbc.queryForObject("SELECT count(*) FROM courier_profiles p"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var rows=jdbc.query(courierSelect()+where+" ORDER BY p.id LIMIT ? OFFSET ?",this::courier,args.toArray());
        return new PageResult<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    @Transactional(readOnly=true)
    public AvailableCourier availability(Long id) {
        return jdbc.query(courierSelect()+" WHERE p.id=?",this::courier,id).stream().findFirst()
            .orElseThrow(()->CourierException.missing("Courier profile not found."));
    }
    @Transactional
    public Profile changeAvailability(Long userId,Availability value) {
        var current=profileService.operational(userId);
        if(current.availability==value)return Profile.from(current);
        var updated=profileService.availability(userId,value);
        outbox.save(CourierOutbox.AVAILABILITY,"CourierAvailabilityChanged","courier:"+updated.id(),
            Map.of("courierId",updated.id(),"courierUserId",updated.userId(),"availability",value,"active",updated.active(),"version",updated.version()));
        return updated;
    }
    @Transactional
    public Profile changeActive(Long id,boolean active,String token) {
        var current=profiles.findById(id).orElseThrow(()->CourierException.missing("Courier profile not found."));
        boolean wasActive=current.active;Availability previous=current.availability;
        var updated=profileService.active(id,active,token);
        if(wasActive!=updated.active()||previous!=updated.availability())
            outbox.save(CourierOutbox.AVAILABILITY,"CourierAvailabilityChanged","courier:"+updated.id(),
                Map.of("courierId",updated.id(),"courierUserId",updated.userId(),"availability",updated.availability(),"active",updated.active(),"version",updated.version()));
        return updated;
    }

    /** Called by the existing evidence workflow; status/evidence confirmation/outbox commit together. */
    @Transactional
    public void changeStatus(UUID parcelId,StatusUpdate update) {
        var parcel=parcel(parcelId,true);
        var courier=profiles.lockById(update.courierId()).orElseThrow(()->rejected("Courier profile not found."));
        if(!courier.active||!courier.userId.equals(update.actorId()))throw rejected("Invalid courier identity.");
        var checkpoints=jdbc.query("""
            SELECT * FROM delivery_checkpoints WHERE id=? FOR UPDATE
            """,(rs,row)->new SavedCheckpoint(rs.getLong("courier_id"),rs.getObject("parcel_id",UUID.class),
                rs.getObject("assignment_id",UUID.class),rs.getLong("assignment_version"),rs.getInt("attempt_no"),
                rs.getString("status"),rs.getString("sync_status"),rs.getString("note"),rs.getString("location_description")),update.submissionId());
        if(checkpoints.isEmpty())throw rejected("Save the delivery submission first.");
        var checkpoint=checkpoints.getFirst();
        if(!Objects.equals(checkpoint.courier(),courier.id)||!parcelId.equals(checkpoint.parcel())
                ||!Objects.equals(checkpoint.assignment(),update.assignmentId())||checkpoint.version()!=update.assignmentVersion()
                ||checkpoint.attempt()!=update.attemptNo()||!checkpoint.status().equals(update.status().name())||!Objects.equals(checkpoint.note(),update.note()))
            throw rejected("Submission does not match the saved evidence.");
        if("CONFIRMED".equals(checkpoint.sync()))return;
        var current=assigned(parcelId,true).orElseThrow(()->rejected("Assignment not found."));
        if(!current.active()||!current.courier().equals(courier.dispatchCourierId)
                ||!current.id().equals(update.assignmentId())||current.version()!=update.assignmentVersion()||current.attempt()!=update.attemptNo())
            throw rejected("The Dispatch assignment changed or is inactive.");
        if(!NEXT.getOrDefault(parcel.status(),Set.of()).contains(update.status().name()))
            throw rejected("This delivery status transition is not allowed.");
        Instant at=Instant.now();
        jdbc.update("UPDATE courier_delivery_parcels SET status=?,status_at=? WHERE parcel_id=?",update.status().name(),Timestamp.from(at),parcelId);
        if(update.status().terminal())jdbc.update("UPDATE courier_delivery_assignments SET active=false WHERE parcel_id=?",parcelId);
        history(update.submissionId(),parcelId,update.status().name(),at,checkpoint.location(),current.attempt(),"COURIER");
        jdbc.update("UPDATE delivery_checkpoints SET sync_status='CONFIRMED',lease_until=NULL,version=version+1 WHERE id=?",update.submissionId());
        var event=new LinkedHashMap<String,Object>();event.put("submissionId",update.submissionId());event.put("parcelId",parcelId);
        event.put("trackingNumber",parcel.tracking());event.put("merchantUserId",parcel.merchant());event.put("courierId",courier.id);
        event.put("courierUserId",courier.userId);event.put("assignmentId",current.id());event.put("assignmentVersion",current.version());
        event.put("attemptNo",current.attempt());event.put("oldStatus",parcel.status());event.put("newStatus",update.status());event.put("changedAt",at);
        outbox.save(CourierOutbox.STATUS,"CourierDeliveryStatusChanged","parcel:"+parcelId,event);
    }
    private record SavedCheckpoint(Long courier,UUID parcel,UUID assignment,long version,int attempt,String status,String sync,String note,String location) {}

    @Transactional(readOnly=true)
    public Activity courierActivity(Long userId,UUID parcelId) {
        var courier=profileService.operational(userId);
        var current=assigned(parcelId,false).orElseThrow(()->CourierException.missing("Assignment not found."));
        if(!courier.dispatchCourierId.equals(current.courier()))throw CourierException.forbidden("This delivery belongs to another courier.");
        return activity(parcelId);
    }
    private Activity activity(UUID parcelId) {
        var current=assigned(parcelId,false).orElseThrow(()->CourierException.missing("Assignment not found."));
        return jdbc.queryForObject("SELECT status,status_at FROM courier_delivery_parcels WHERE parcel_id=?",(rs,row)->new Activity(parcelId,
            rs.getString("status"),current.id(),current.version(),current.attempt(),rs.getTimestamp("status_at")==null?null:rs.getTimestamp("status_at").toInstant()),parcelId);
    }

    private Parcel parcel(UUID id,boolean lock) {
        return jdbc.query("SELECT merchant_user_id,status,delivery_date,tracking_number FROM courier_delivery_parcels WHERE parcel_id=?"+(lock?" FOR UPDATE":""),
            (rs,row)->new Parcel(rs.getObject("merchant_user_id",Long.class),rs.getString("status"),rs.getObject("delivery_date",LocalDate.class),rs.getString("tracking_number")),id)
            .stream().findFirst().orElseThrow(()->CourierException.missing("Parcel data has not been imported into Courier."));
    }
    private Optional<Assigned> assigned(UUID id,boolean lock) {
        return jdbc.query("SELECT * FROM courier_delivery_assignments WHERE parcel_id=?"+(lock?" FOR UPDATE":""),
            (rs,row)->new Assigned(rs.getObject("projection_id",UUID.class),rs.getObject("dispatch_courier_id",UUID.class),rs.getInt("attempt_no"),
                rs.getLong("assignment_version"),rs.getTimestamp("assigned_at").toInstant(),rs.getObject("delivery_date",LocalDate.class),rs.getBoolean("active"),rs.getBoolean("managed_locally")),id).stream().findFirst();
    }
    private void history(UUID id,UUID parcel,String status,Instant at,String location,int attempt,String role) {
        jdbc.update("INSERT INTO courier_delivery_history(id,parcel_id,status,recorded_at,location_description,attempt_no,actor_role) VALUES (?,?,?,?,?,?,?)",
            id,parcel,status,Timestamp.from(at),location,attempt,role);
    }
    private String courierSelect() {
        return """
            SELECT p.*,(SELECT count(*) FROM courier_delivery_assignments a JOIN courier_delivery_parcels d ON d.parcel_id=a.parcel_id
                WHERE a.dispatch_courier_id=p.dispatch_courier_id AND a.active=true AND d.status NOT IN ('DELIVERED','FAILED','CANCELLED')) AS active_count
            FROM courier_profiles p
            """;
    }
    private AvailableCourier courier(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new AvailableCourier(rs.getLong("id"),rs.getObject("dispatch_courier_id",UUID.class),rs.getString("name"),
            VehicleType.valueOf(rs.getString("vehicle_type")),rs.getString("area"),Availability.valueOf(rs.getString("availability")),rs.getBoolean("active"),rs.getLong("active_count"));
    }
    private static void paging(int page,int size) {if(page<0||size<1||size>100)throw CourierException.invalid("Invalid pagination.");}
    private static RemoteFailure rejected(String message) {return new RemoteFailure("Courier delivery",true,message);}
}
