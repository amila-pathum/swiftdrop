package com.swiftdrop.courier.service;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.dto.CourierWorkflowDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.event.CourierOutbox;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Courier-owned assignments and activity. Merchant ownership is never taken from a request body. */
@Service
public class CourierWorkflowService {
    private static final Set<String> STATUSES=Set.of("CREATED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","CANCELLED");
    private static final Map<String,Set<String>> NEXT=Map.of("CREATED",Set.of("PICKED_UP"),"PICKED_UP",Set.of("IN_TRANSIT"),
        "IN_TRANSIT",Set.of("OUT_FOR_DELIVERY"),"OUT_FOR_DELIVERY",Set.of("DELIVERED","FAILED"));
    private final JdbcTemplate jdbc;
    private final CourierProfileRepository profiles;
    private final CourierProfileService profileService;
    private final CourierDeliveryReadRepository reads;
    private final CourierOutbox outbox;
    private final ObjectMapper mapper;

    public CourierWorkflowService(JdbcTemplate jdbc,CourierProfileRepository profiles,CourierProfileService profileService,
            CourierDeliveryReadRepository reads,CourierOutbox outbox,ObjectMapper mapper) {
        this.jdbc=jdbc;this.profiles=profiles;this.profileService=profileService;this.reads=reads;this.outbox=outbox;this.mapper=mapper;
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

    @Transactional
    public AssignmentReceipt assign(Long merchant,AssignmentInput input) {
        String fingerprint=digest("assign|"+input.parcelId()+"|"+input.courierId()+"|"+input.deliveryDate());
        var replay=replay(input.submissionId(),merchant,fingerprint);if(replay!=null)return replay;
        var parcel=parcel(input.parcelId(),true);owner(parcel,merchant);
        replay=replay(input.submissionId(),merchant,fingerprint);if(replay!=null)return replay;
        if(!Set.of("CREATED","FAILED").contains(parcel.status()==null?"":parcel.status()))
            throw CourierException.conflict("Assign or reassign before pickup, or create a new attempt after failure.");
        var courier=profiles.lockById(input.courierId()).orElseThrow(()->CourierException.missing("Courier profile not found."));
        if(!courier.active||courier.availability!=Availability.AVAILABLE)
            throw CourierException.conflict("Select an active, AVAILABLE courier.");
        var previous=assigned(input.parcelId(),true).orElse(null);
        if(previous!=null&&previous.local()&&previous.active()&&previous.courier().equals(courier.dispatchCourierId)&&"CREATED".equals(parcel.status()))
            throw CourierException.conflict("This delivery is already assigned to that courier.");
        int attempt=previous==null?1:previous.attempt()+("FAILED".equals(parcel.status())?1:0);
        long version=previous==null?0:previous.version()+1;
        UUID id=UUID.randomUUID();Instant at=Instant.now();LocalDate date=input.deliveryDate()==null?parcel.date():input.deliveryDate();
        jdbc.update("""
            INSERT INTO courier_delivery_assignments(parcel_id,projection_id,dispatch_assignment_id,dispatch_courier_id,
                assigned_at,delivery_date,attempt_no,assignment_version,managed_locally,active) VALUES (?,?,?,?,?,?,?,?,true,true)
            ON CONFLICT(parcel_id) DO UPDATE SET projection_id=excluded.projection_id,dispatch_assignment_id=excluded.dispatch_assignment_id,
                dispatch_courier_id=excluded.dispatch_courier_id,assigned_at=excluded.assigned_at,delivery_date=excluded.delivery_date,
                attempt_no=excluded.attempt_no,assignment_version=excluded.assignment_version,managed_locally=true,active=true
            """,input.parcelId(),id,id,courier.dispatchCourierId,Timestamp.from(at),date,attempt,version);
        if("FAILED".equals(parcel.status())) {
            jdbc.update("UPDATE courier_delivery_parcels SET status='CREATED',status_at=? WHERE parcel_id=?",Timestamp.from(at),input.parcelId());
            history(UUID.randomUUID(),input.parcelId(),"CREATED",at,null,attempt,"MERCHANT");
        }
        var result=new AssignmentReceipt(id,input.parcelId(),courier.id,courier.dispatchCourierId,merchant,attempt,version,true,date,at,"CREATED");
        var event=assignmentData(result,input.submissionId(),parcel.tracking());
        event.put("previousCourierReference",previous==null?null:previous.courier());
        outbox.save(CourierOutbox.ASSIGNED,"CourierDeliveryAssigned","parcel:"+input.parcelId(),event);
        remember(input.submissionId(),merchant,input.parcelId(),fingerprint,result);
        return result;
    }

    @Transactional
    public AssignmentReceipt cancel(Long merchant,UUID parcelId,CancelInput input) {
        String note=clean(input.note());String fingerprint=digest("cancel|"+parcelId+"|"+note);
        var replay=replay(input.submissionId(),merchant,fingerprint);if(replay!=null)return replay;
        var parcel=parcel(parcelId,true);owner(parcel,merchant);
        replay=replay(input.submissionId(),merchant,fingerprint);if(replay!=null)return replay;
        var current=assigned(parcelId,true).orElseThrow(()->CourierException.missing("Assignment not found."));
        if(!current.local()||!current.active()||!"CREATED".equals(parcel.status()))
            throw CourierException.conflict("Only a Courier-managed delivery awaiting pickup can be cancelled.");
        var courier=profile(current.courier());Instant at=Instant.now();long version=current.version()+1;
        jdbc.update("UPDATE courier_delivery_assignments SET active=false,assignment_version=? WHERE parcel_id=?",version,parcelId);
        jdbc.update("UPDATE courier_delivery_parcels SET status='CANCELLED',status_at=? WHERE parcel_id=?",Timestamp.from(at),parcelId);
        history(input.submissionId(),parcelId,"CANCELLED",at,null,current.attempt(),"MERCHANT");
        var result=new AssignmentReceipt(current.id(),parcelId,courier.id,courier.dispatchCourierId,merchant,current.attempt(),version,false,
            current.date(),current.at(),"CANCELLED");
        var event=assignmentData(result,input.submissionId(),parcel.tracking());event.put("note",note);
        outbox.save(CourierOutbox.CANCELLED,"CourierDeliveryAssignmentCancelled","parcel:"+parcelId,event);
        remember(input.submissionId(),merchant,parcelId,fingerprint,result);return result;
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
        if(!current.local()||!current.active()||!current.courier().equals(courier.dispatchCourierId)
                ||!current.id().equals(update.assignmentId())||current.version()!=update.assignmentVersion()||current.attempt()!=update.attemptNo())
            throw rejected("The assignment changed or is not managed by Courier.");
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
    public PageResult<CourierDeliveryService.Delivery> merchantDeliveries(Long merchant,String status,int page,int size) {
        paging(page,size);var args=new ArrayList<Object>();args.add(merchant);
        String where=" WHERE p.merchant_user_id=?";
        if(status!=null&&!status.isBlank()) {
            if(!STATUSES.contains(status))throw CourierException.invalid("Invalid delivery status.");
            where+=" AND p.status=?";args.add(status);
        }
        String from=" FROM courier_delivery_assignments a JOIN courier_delivery_parcels p ON p.parcel_id=a.parcel_id";
        long total=jdbc.queryForObject("SELECT count(*)"+from+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var ids=jdbc.query("SELECT a.parcel_id"+from+where+" ORDER BY a.assigned_at DESC,a.parcel_id LIMIT ? OFFSET ?",
            (rs,row)->rs.getObject(1,UUID.class),args.toArray());
        return new PageResult<>(ids.stream().map(id->merchantDetail(merchant,id)).toList(),page,size,total,(int)((total+size-1)/size));
    }
    @Transactional(readOnly=true)
    public CourierDeliveryService.Delivery merchantDetail(Long merchant,UUID parcelId) {
        owner(parcel(parcelId,false),merchant);
        var assignment=assigned(parcelId,false).orElseThrow(()->CourierException.missing("Assignment not found."));
        var courier=profile(assignment.courier());
        return new CourierDeliveryService.Delivery(new Assignment(assignment.id(),parcelId,courier.id,assignment.attempt(),assignment.active(),
            assignment.version(),assignment.date(),assignment.at()),reads.parcel(parcelId));
    }
    @Transactional(readOnly=true)
    public Activity merchantActivity(Long merchant,UUID parcelId) { owner(parcel(parcelId,false),merchant);return activity(parcelId); }
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
    private CourierProfile profile(UUID reference) {return profiles.findByDispatchCourierId(reference).orElseThrow(()->CourierException.missing("Courier profile is not linked."));}
    private void owner(Parcel parcel,Long merchant) {
        if(parcel.merchant()==null)throw CourierException.conflict("Import the verified merchant user ID for this parcel first.");
        if(!parcel.merchant().equals(merchant))throw CourierException.forbidden("This parcel belongs to another merchant.");
    }
    private void history(UUID id,UUID parcel,String status,Instant at,String location,int attempt,String role) {
        jdbc.update("INSERT INTO courier_delivery_history(id,parcel_id,status,recorded_at,location_description,attempt_no,actor_role) VALUES (?,?,?,?,?,?,?)",
            id,parcel,status,Timestamp.from(at),location,attempt,role);
    }
    private AssignmentReceipt replay(UUID id,Long merchant,String fingerprint) {
        var rows=jdbc.query("SELECT merchant_user_id,fingerprint,response FROM courier_assignment_requests WHERE submission_id=?",
            (rs,row)->new Cached(rs.getLong(1),rs.getString(2),rs.getString(3)),id);
        if(rows.isEmpty())return null;var result=rows.getFirst();
        if(!merchant.equals(result.merchant()))throw CourierException.forbidden("This submission belongs to another merchant.");
        if(!fingerprint.equals(result.fingerprint()))throw CourierException.conflict("Submission ID was reused with different content.");
        return mapper.readValue(result.response(),AssignmentReceipt.class);
    }
    private record Cached(Long merchant,String fingerprint,String response) {}
    private void remember(UUID id,Long merchant,UUID parcel,String fingerprint,AssignmentReceipt response) {
        jdbc.update("INSERT INTO courier_assignment_requests(submission_id,merchant_user_id,parcel_id,fingerprint,response) VALUES (?,?,?,?,?)",
            id,merchant,parcel,fingerprint,mapper.writeValueAsString(response));
    }
    private Map<String,Object> assignmentData(AssignmentReceipt value,UUID submission,String tracking) {
        var event=new LinkedHashMap<String,Object>();event.put("submissionId",submission);event.put("assignmentId",value.assignmentId());event.put("parcelId",value.parcelId());
        event.put("trackingNumber",tracking);event.put("courierId",value.courierId());event.put("courierReference",value.courierReference());
        event.put("merchantUserId",value.merchantUserId());event.put("attemptNo",value.attemptNo());event.put("assignmentVersion",value.version());
        event.put("deliveryDate",value.deliveryDate());event.put("assignedAt",value.assignedAt());event.put("active",value.active());return event;
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
    private static String clean(String value) {return value==null||value.isBlank()?null:value.trim();}
    private static String digest(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static RemoteFailure rejected(String message) {return new RemoteFailure("Courier delivery",true,message);}
}
