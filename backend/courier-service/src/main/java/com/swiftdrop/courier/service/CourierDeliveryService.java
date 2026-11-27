package com.swiftdrop.courier.service;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.swiftdrop.courier.photo.DeliveryPhotoService;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CourierDeliveryService {
    private final CourierProfileService profiles;
    private final DispatchClient dispatch;
    private final ParcelClient parcels;
    private final DeliveryCheckpointRepository checkpoints;
    private final DeliveryProofRepository proofs;
    private final DeliveryPhotoService photos;
    private final TransactionTemplate transactions;
    public CourierDeliveryService(CourierProfileService profiles,DispatchClient dispatch,ParcelClient parcels,
        DeliveryCheckpointRepository checkpoints,DeliveryProofRepository proofs,DeliveryPhotoService photos,PlatformTransactionManager manager) {
        this.profiles=profiles;this.dispatch=dispatch;this.parcels=parcels;this.checkpoints=checkpoints;this.proofs=proofs;this.photos=photos;
        transactions=new TransactionTemplate(manager);
    }
    public record Delivery(Assignment assignment,ParcelDetails parcel) {}
    public PageResult<Delivery> deliveries(Long userId,String filter,int page,int size,String token) {
        var courier=profiles.operational(userId);var assignments=assigned(courier.id,token);
        if(!Set.of("today","active","history").contains(filter))throw CourierException.invalid("Filter must be today, active or history.");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Colombo"));
        var selected=new ArrayList<Delivery>();
        // Assignment and parcel reads use Courier's RabbitMQ projection.
        for(var assignment:assignments) {
            // Include unscheduled work assigned today without inventing a schedule.
            LocalDate workDate=assignment.deliveryDate()!=null?assignment.deliveryDate():
                assignment.assignedAt()==null?null:assignment.assignedAt().atZone(ZoneId.of("Asia/Colombo")).toLocalDate();
            if(filter.equals("today")&&!today.equals(workDate))continue;
            var parcel=parcel(assignment.parcelId(),token);
            boolean terminal=Set.of("DELIVERED","FAILED","CANCELLED").contains(parcel.status());
            if(filter.equals("active")&&(!assignment.active()||terminal))continue;
            if(filter.equals("history")&&assignment.active()&&!terminal)continue;
            selected.add(new Delivery(assignment,parcel));
        }
        int start=(int)Math.min((long)page*size,selected.size()),end=Math.min(start+size,selected.size());
        return new PageResult<>(List.copyOf(selected.subList(start,end)),page,size,selected.size(),(selected.size()+size-1)/size);
    }
    public Delivery detail(Long userId,UUID parcelId,String token) {
        var courier=profiles.operational(userId);var assignment=assignment(courier.id,parcelId,false,token);
        return new Delivery(assignment,parcel(parcelId,token));
    }
    private List<Assignment> assigned(Long courierId,String token) {
        var result=dispatch.assignments(courierId,token);
        if(result==null || result.stream().anyMatch(a->a==null||a.id()==null||a.parcelId()==null||!courierId.equals(a.courierId())||a.attemptNo()<1||a.version()<0))
            throw new RemoteFailure("Assignment data",false);
        return result;
    }
    private Assignment assignment(Long courierId,UUID parcelId,boolean active,String token) {
        return assigned(courierId,token).stream().filter(a->parcelId.equals(a.parcelId())&&(!active||a.active()))
            .max(Comparator.comparingInt(Assignment::attemptNo)).orElseThrow(()->CourierException.forbidden("This parcel is not assigned to your courier profile."));
    }
    private ParcelDetails parcel(UUID id,String token) {
        var p=parcels.parcel(id,token);
        if(p==null||!id.equals(p.id())||p.status()==null)throw new RemoteFailure("Parcel service",false);
        return p;
    }
    /** Hands out a signed URL the courier's browser uploads one optional photo to. */
    public DeliveryPhotoService.Ticket photoUpload(Long userId,UUID parcelId,PhotoUploadInput input,String token) {
        var courier=profiles.operational(userId);
        assignment(courier.id,parcelId,true,token);
        return photos.issue(courier.id,parcelId,input.contentType(),input.sizeBytes());
    }
    public Submission submit(Long userId,UUID parcelId,DeliveryInput input,String token) {
        var courier=profiles.operational(userId);
        validate(input);
        String photoKey=clean(input.photoKey());
        String fingerprint=fingerprint(parcelId,input);
        var existing=checkpoints.findById(input.submissionId());
        if(existing.isPresent()) {
            var current=existing.get();verifySame(current,courier.id,fingerprint);
            if(current.syncStatus==SyncStatus.CONFIRMED||current.syncStatus==SyncStatus.REJECTED)return response(current);
        }
        var assignment=assignment(courier.id,parcelId,true,token);
        var parcel=parcel(parcelId,token);
        // A retry of a stored submission reuses its already-checked photo.
        if(photoKey!=null&&existing.isEmpty())photos.verify(courier.id,parcelId,photoKey);
        final boolean[] claimed={false};
        DeliveryCheckpoint operation=transactions.execute(tx->{
            var previous=checkpoints.lockById(input.submissionId());
            if(previous.isPresent()) {
                var c=previous.get();verifySame(c,courier.id,fingerprint);
                if(c.syncStatus==SyncStatus.REJECTED||c.syncStatus==SyncStatus.CONFIRMED||c.leaseUntil!=null&&c.leaseUntil.isAfter(Instant.now()))return c;
                if(!c.assignmentId.equals(assignment.id())||c.assignmentVersion!=assignment.version())throw CourierException.conflict("Assignment changed; refresh the delivery before submitting again.");
                c.leaseUntil=Instant.now().plusSeconds(30);c.syncStatus=SyncStatus.PENDING;claimed[0]=true;
                return checkpoints.saveAndFlush(c);
            }
            var c=new DeliveryCheckpoint();c.id=input.submissionId();c.parcelId=parcelId;c.courier=courier;
            c.assignmentId=assignment.id();c.assignmentVersion=assignment.version();c.attemptNo=assignment.attemptNo();c.status=input.status();
            c.plannedAddressSnapshot=input.status()==DeliveryStatus.PICKED_UP?parcel.pickupAddress():parcel.deliveryAddress();
            c.locationDescription=clean(input.locationDescription());c.latitude=input.latitude();c.longitude=input.longitude();
            c.accuracyMeters=input.accuracyMeters();c.capturedAt=input.capturedAt();c.recordedAt=Instant.now();
            c.note=clean(input.note());c.fingerprint=fingerprint;c.leaseUntil=Instant.now().plusSeconds(30);
            c.photoKey=photoKey;
            if(photoKey!=null)photos.markUsed(photoKey);
            checkpoints.saveAndFlush(c);
            if(input.status().terminal()) {
                var p=new DeliveryProof();p.parcelId=parcelId;p.courier=courier;p.checkpoint=c;p.attemptNo=c.attemptNo;
                p.outcome=input.status();p.recipientName=clean(input.recipientName());
                p.failureReason=input.failureReason();p.note=c.note;p.recordedAt=c.recordedAt;
                p.collectionAddressSnapshot=parcel.pickupAddress();p.deliveryAddressSnapshot=parcel.deliveryAddress();
                proofs.saveAndFlush(p);
            }
            claimed[0]=true;return c;
        });
        if(!claimed[0])return response(operation);
        SyncStatus outcome;
        try {
            parcels.update(parcelId,new StatusUpdate(input.status(),clean(input.note()),userId,courier.id,
                assignment.id(),assignment.version(),assignment.attemptNo(),input.submissionId()),token);
            outcome=SyncStatus.CONFIRMED;
        } catch(RemoteFailure e) {outcome=e.rejected?SyncStatus.REJECTED:SyncStatus.UNKNOWN;}
        final SyncStatus resolved=outcome;
        return transactions.execute(tx->{
            var c=checkpoints.lockById(input.submissionId()).orElseThrow();c.syncStatus=resolved;
            c.leaseUntil=resolved==SyncStatus.UNKNOWN?Instant.now().plusSeconds(30):null;
            if(resolved==SyncStatus.REJECTED) {
                proofs.findByCheckpointId(c.id).ifPresent(proof->{proofs.delete(proof);proofs.flush();});
                String rejectedPhoto=c.photoKey;
                if(rejectedPhoto!=null) {
                    c.photoKey=null;
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            public void afterCommit(){photos.deleteFile(rejectedPhoto);}
                        });
                }
            }
            checkpoints.saveAndFlush(c);return response(c);
        });
    }
    private void verifySame(DeliveryCheckpoint c,Long courierId,String fingerprint) {
        if(!courierId.equals(c.courier.id))throw CourierException.forbidden("This submission belongs to another courier.");
        if(!c.fingerprint.equals(fingerprint))throw CourierException.conflict("Submission ID was already used with different content.");
    }
    private String fingerprint(UUID parcelId,DeliveryInput input) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((parcelId+"|"+input).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void validate(DeliveryInput i) {
        if((i.latitude()==null)!=(i.longitude()==null))throw CourierException.invalid("Provide both latitude and longitude.");
        if(i.latitude()==null&&(i.accuracyMeters()!=null||i.capturedAt()!=null))throw CourierException.invalid("Coordinates are required with GPS accuracy or capture time.");
        // Location (GPS or description) is optional for every update.
        // A photo is optional at every stage; a delivery always names who received it.
        if(i.status()==DeliveryStatus.DELIVERED&&clean(i.recipientName())==null)throw CourierException.invalid("Delivery requires the recipient name.");
        if(i.status()==DeliveryStatus.FAILED&&i.failureReason()==null)throw CourierException.invalid("Failed delivery requires a reason.");
        if(i.status()!=DeliveryStatus.FAILED&&i.failureReason()!=null)throw CourierException.invalid("Failure reason is allowed only for FAILED.");
        if(i.status()!=DeliveryStatus.DELIVERED&&clean(i.recipientName())!=null)throw CourierException.invalid("Recipient name is accepted only for DELIVERED.");
        if(i.failureReason()==FailureReason.OTHER&&clean(i.note())==null)throw CourierException.invalid("Explain OTHER failure reasons in the note.");
    }
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private Submission response(DeliveryCheckpoint c) {
        String message=switch(c.syncStatus){case CONFIRMED->"Delivery update and evidence saved.";case REJECTED->"Delivery update rejected. Check the current status and assignment before creating a new submission.";case UNKNOWN->"Evidence saved; confirmation is unknown. Retry the identical submission after 30 seconds.";case PENDING->"Submission is processing. Poll its status before retrying.";};
        return new Submission(c.id,c.parcelId,c.syncStatus,message);
    }
    @Transactional(readOnly=true)
    public Submission submission(Long userId,UUID id) {
        var p=profiles.operational(userId);var c=checkpoints.findById(id).orElseThrow(()->CourierException.missing("Submission not found."));
        if(!p.id.equals(c.courier.id))throw CourierException.forbidden("This submission belongs to another courier.");return response(c);
    }
    @Transactional(readOnly=true)
    public List<Checkpoint> tracking(Long userId,UUID parcelId,String token) {
        var p=profiles.operational(userId);assignment(p.id,parcelId,false,token);
        return checkpoints.findByParcelIdAndCourierIdAndSyncStatusOrderByRecordedAtAsc(parcelId,p.id,SyncStatus.CONFIRMED).stream()
            .map(c->Checkpoint.from(c,photos.viewUrl(c.photoKey))).toList();
    }
    @Transactional(readOnly=true)
    public List<Proof> proofs(Long userId,UUID parcelId,String token) {
        var p=profiles.operational(userId);assignment(p.id,parcelId,false,token);
        return proofs.findByParcelIdAndCourierIdAndCheckpointSyncStatusOrderByRecordedAtAsc(parcelId,p.id,SyncStatus.CONFIRMED).stream()
            .map(proof->Proof.from(proof,photos.viewUrl(proof.checkpoint.photoKey))).toList();
    }
    /** Every confirmed courier update on a parcel, with its photo, for dispatchers and admins. */
    @Transactional(readOnly=true)
    public List<DispatchUpdate> dispatchUpdates(UUID parcelId) {
        var outcomes=new HashMap<UUID,DeliveryProof>();
        for(var proof:proofs.findByParcelIdAndCheckpointSyncStatus(parcelId,SyncStatus.CONFIRMED))outcomes.put(proof.checkpoint.id,proof);
        return checkpoints.findByParcelIdAndSyncStatusOrderByRecordedAtAsc(parcelId,SyncStatus.CONFIRMED).stream().map(c->{
            var proof=outcomes.get(c.id);
            return new DispatchUpdate(c.id,c.status,c.attemptNo,c.recordedAt,c.courier.id,c.courier.name,c.note,
                proof==null?null:proof.recipientName,proof==null?null:proof.failureReason,photos.viewUrl(c.photoKey));
        }).toList();
    }
}
