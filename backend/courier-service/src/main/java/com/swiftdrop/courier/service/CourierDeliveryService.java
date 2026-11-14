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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
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
    private final ProofStorageService storage;
    private final TransactionTemplate transactions;
    public CourierDeliveryService(CourierProfileService profiles,DispatchClient dispatch,ParcelClient parcels,
        DeliveryCheckpointRepository checkpoints,DeliveryProofRepository proofs,ProofStorageService storage,PlatformTransactionManager manager) {
        this.profiles=profiles;this.dispatch=dispatch;this.parcels=parcels;this.checkpoints=checkpoints;this.proofs=proofs;this.storage=storage;
        transactions=new TransactionTemplate(manager);
    }
    public record Delivery(Assignment assignment,ParcelDetails parcel) {}
    public PageResult<Delivery> deliveries(Long userId,String filter,int page,int size,String token) {
        var courier=profiles.operational(userId);var assignments=assigned(courier.id,token);
        if(!Set.of("today","active","history").contains(filter))throw CourierException.invalid("Filter must be today, active or history.");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Colombo"));
        var selected=new ArrayList<Delivery>();
        // Bound remote fan-out until Dispatch provides a paginated/bulk contract.
        if(assignments.size()>200)throw CourierException.conflict("Too many assignments; use the agreed paginated Dispatch contract.");
        for(var assignment:assignments) {
            if(filter.equals("today")&&!today.equals(assignment.deliveryDate()))continue;
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
            throw new RemoteFailure("Dispatch service",false);
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
    public Submission submit(Long userId,UUID parcelId,DeliveryInput input,MultipartFile upload,String token) {
        var courier=profiles.operational(userId);
        var photo=storage.validate(upload);validate(input,photo);
        String fingerprint=fingerprint(parcelId,input,photo);
        var existing=checkpoints.findById(input.submissionId());
        if(existing.isPresent()) {
            var current=existing.get();verifySame(current,courier.id,fingerprint);
            if(current.syncStatus==SyncStatus.CONFIRMED||current.syncStatus==SyncStatus.REJECTED)return response(current);
        }
        var assignment=assignment(courier.id,parcelId,true,token);
        var parcel=parcel(parcelId,token);
        String photoKey=existing.isEmpty()?storage.store(photo):null;
        final boolean[] claimed={false};
        DeliveryCheckpoint operation;
        try {
            operation=transactions.execute(tx->{
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
                checkpoints.saveAndFlush(c);
                if(input.status().terminal()) {
                    var p=new DeliveryProof();p.parcelId=parcelId;p.courier=courier;p.checkpoint=c;p.attemptNo=c.attemptNo;
                    p.outcome=input.status();p.recipientName=clean(input.recipientName());p.photoKey=photoKey;
                    p.failureReason=input.failureReason();p.note=c.note;p.recordedAt=c.recordedAt;
                    p.collectionAddressSnapshot=parcel.pickupAddress();p.deliveryAddressSnapshot=parcel.deliveryAddress();
                    proofs.saveAndFlush(p);
                }
                claimed[0]=true;return c;
            });
        } catch(RuntimeException e) {storage.discard(photoKey);throw e;}
        if(!claimed[0]){storage.discard(photoKey);return response(operation);}
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
            if(resolved==SyncStatus.REJECTED)proofs.findByCheckpointId(c.id).ifPresent(proof->{
                proofs.delete(proof);proofs.flush();
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        public void afterCommit(){storage.discard(proof.photoKey);}
                    });
            });
            checkpoints.saveAndFlush(c);return response(c);
        });
    }
    private void verifySame(DeliveryCheckpoint c,Long courierId,String fingerprint) {
        if(!courierId.equals(c.courier.id))throw CourierException.forbidden("This submission belongs to another courier.");
        if(!c.fingerprint.equals(fingerprint))throw CourierException.conflict("Submission ID was already used with different content.");
    }
    private String fingerprint(UUID parcelId,DeliveryInput input,ProofStorageService.Photo photo) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((parcelId+"|"+input+"|"+(photo==null?"":photo.digest())).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void validate(DeliveryInput i,ProofStorageService.Photo photo) {
        if((i.latitude()==null)!=(i.longitude()==null))throw CourierException.invalid("Provide both latitude and longitude.");
        if(i.latitude()==null&&(i.accuracyMeters()!=null||i.capturedAt()!=null))throw CourierException.invalid("Coordinates are required with GPS accuracy or capture time.");
        if((i.status()==DeliveryStatus.PICKED_UP||i.status().terminal())&&i.latitude()==null&&clean(i.locationDescription())==null)
            throw CourierException.invalid("Record the collection/attempt location using GPS or a location description.");
        if(i.status()==DeliveryStatus.DELIVERED&&clean(i.recipientName())==null&&photo==null)throw CourierException.invalid("Delivery requires a recipient name or photo.");
        if(i.status()==DeliveryStatus.FAILED&&i.failureReason()==null)throw CourierException.invalid("Failed delivery requires a reason.");
        if(i.status()!=DeliveryStatus.FAILED&&i.failureReason()!=null)throw CourierException.invalid("Failure reason is allowed only for FAILED.");
        if(!i.status().terminal()&&(photo!=null||clean(i.recipientName())!=null))throw CourierException.invalid("Proof fields are accepted only for final outcomes.");
        if(i.failureReason()==FailureReason.OTHER&&clean(i.note())==null)throw CourierException.invalid("Explain OTHER failure reasons in the note.");
    }
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private Submission response(DeliveryCheckpoint c) {
        String message=switch(c.syncStatus){case CONFIRMED->"Parcel update and evidence confirmed.";case REJECTED->"Parcel service rejected this update. Refresh before creating a new submission.";case UNKNOWN->"Evidence saved; parcel confirmation is unknown. Retry the identical submission after 30 seconds.";case PENDING->"Submission is processing. Poll its status before retrying.";};
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
        return checkpoints.findByParcelIdAndCourierIdAndSyncStatusOrderByRecordedAtAsc(parcelId,p.id,SyncStatus.CONFIRMED).stream().map(Checkpoint::from).toList();
    }
    @Transactional(readOnly=true)
    public List<Proof> proofs(Long userId,UUID parcelId,String token) {
        var p=profiles.operational(userId);assignment(p.id,parcelId,false,token);
        return proofs.findByParcelIdAndCourierIdAndCheckpointSyncStatusOrderByRecordedAtAsc(parcelId,p.id,SyncStatus.CONFIRMED).stream().map(Proof::from).toList();
    }
    @Transactional(readOnly=true)
    public Resource photo(Long userId,UUID parcelId,UUID proofId,String token) {
        var p=profiles.operational(userId);assignment(p.id,parcelId,false,token);
        var proof=proofs.findByIdAndParcelIdAndCourierIdAndCheckpointSyncStatus(proofId,parcelId,p.id,SyncStatus.CONFIRMED).orElseThrow(()->CourierException.missing("Proof not found."));
        return storage.load(proof.photoKey);
    }
}
