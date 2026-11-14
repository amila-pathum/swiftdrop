package com.swiftdrop.courier.service;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CourierDeliveryServiceTest {
    CourierProfileService profiles;DispatchClient dispatch;ParcelClient parcels;
    DeliveryCheckpointRepository checkpoints;DeliveryProofRepository proofs;
    ProofStorageService storage;CourierDeliveryService service;CourierProfile courier;
    UUID parcelId;Assignment assignment;Map<UUID,DeliveryCheckpoint> operations;
    @BeforeEach void setup() {
        profiles=mock(CourierProfileService.class);dispatch=mock(DispatchClient.class);parcels=mock(ParcelClient.class);
        checkpoints=mock(DeliveryCheckpointRepository.class);proofs=mock(DeliveryProofRepository.class);storage=mock(ProofStorageService.class);
        var manager=new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object tx,TransactionDefinition definition){}
            protected void doCommit(DefaultTransactionStatus status){}
            protected void doRollback(DefaultTransactionStatus status){}
        };
        service=new CourierDeliveryService(profiles,dispatch,parcels,checkpoints,proofs,storage,manager);
        courier=new CourierProfile();courier.id=10L;courier.userId=42L;courier.active=true;
        parcelId=UUID.randomUUID();assignment=new Assignment(UUID.randomUUID(),parcelId,10L,1,true,0,LocalDate.now(),Instant.now());
        when(profiles.operational(42L)).thenReturn(courier);when(dispatch.assignments(10L,"token")).thenReturn(List.of(assignment));
        when(parcels.parcel(parcelId,"token")).thenReturn(new ParcelDetails(parcelId,"SD1","OUT_FOR_DELIVERY","Collection place","Delivery place","Recipient","0700000000"));
        operations=new HashMap<>();when(checkpoints.findById(any())).thenAnswer(i->Optional.ofNullable(operations.get(i.getArgument(0))));
        when(checkpoints.lockById(any())).thenAnswer(i->Optional.ofNullable(operations.get(i.getArgument(0))));
        when(checkpoints.saveAndFlush(any())).thenAnswer(i->{DeliveryCheckpoint c=i.getArgument(0);operations.put(c.id,c);return c;});
    }
    DeliveryInput input(DeliveryStatus status,String name,FailureReason reason) {
        return new DeliveryInput(UUID.randomUUID(),status,name,reason,null,"Front gate",null,null,null,null);
    }
    @Test void rejectsOtherCouriersAssignmentsBeforeReadingOrUpdatingParcel() {
        when(dispatch.assignments(10L,"token")).thenReturn(List.of());
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.DELIVERED,"Recipient",null),null,"token"))
            .isInstanceOf(CourierException.class).hasMessageContaining("not assigned");
        verifyNoInteractions(parcels);verify(proofs,never()).saveAndFlush(any());
    }
    @Test void rejectsUpstreamAssignmentsForAnotherCourierRatherThanTrustingTheirIds() {
        when(dispatch.assignments(10L,"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,99L,1,true,0,LocalDate.now(),Instant.now())));
        assertThatThrownBy(()->service.detail(42L,parcelId,"token")).isInstanceOf(RemoteFailure.class);
        verifyNoInteractions(parcels);
    }
    @Test void deliveredNeedsNameOrPhotoAndFailedNeedsReason() {
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.DELIVERED," ",null),null,"token")).hasMessageContaining("recipient name or photo");
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.FAILED,null,null),null,"token")).hasMessageContaining("requires a reason");
        verifyNoInteractions(dispatch,parcels);
    }
    @Test void confirmsEvidenceOnlyAfterRemoteSuccessAndRetriesAreIdempotent() {
        var input=input(DeliveryStatus.DELIVERED,"Recipient",null);
        assertThat(service.submit(42L,parcelId,input,null,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        assertThat(service.submit(42L,parcelId,input,null,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        verify(parcels,times(1)).update(eq(parcelId),any(),eq("token"));verify(proofs,times(1)).saveAndFlush(argThat(p->p.attemptNo==1&&p.collectionAddressSnapshot.equals("Collection place")&&p.deliveryAddressSnapshot.equals("Delivery place")));
    }
    @Test void timeoutRetainsEvidenceButDoesNotClaimDeliveryAndLeasePreventsImmediateRetry() {
        doThrow(new RemoteFailure("Parcel service",false)).when(parcels).update(any(),any(),any());
        var input=input(DeliveryStatus.FAILED,null,FailureReason.RECIPIENT_ABSENT);
        assertThat(service.submit(42L,parcelId,input,null,"token").syncStatus()).isEqualTo(SyncStatus.UNKNOWN);
        service.submit(42L,parcelId,input,null,"token");verify(parcels,times(1)).update(any(),any(),any());
        assertThat(operations.get(input.submissionId()).leaseUntil).isAfter(Instant.now());
    }
    @Test void retryCannotChangePreviouslySubmittedContentOrUseAnotherCouriersSubmission() {
        var input=input(DeliveryStatus.DELIVERED,"Recipient",null);service.submit(42L,parcelId,input,null,"token");
        var changed=new DeliveryInput(input.submissionId(),input.status(),"Different",null,null,"Front gate",null,null,null,null);
        assertThatThrownBy(()->service.submit(42L,parcelId,changed,null,"token")).hasMessageContaining("different content");
        var other=new CourierProfile();other.id=99L;other.userId=43L;other.active=true;when(profiles.operational(43L)).thenReturn(other);
        assertThatThrownBy(()->service.submit(43L,parcelId,input,null,"token")).hasMessageContaining("another courier");
    }
    @Test void rejectedOutcomeReleasesFinalProofSoCorrectedSubmissionCanBeMade() {
        doThrow(new RemoteFailure("Parcel service",true)).when(parcels).update(any(),any(),any());
        var proof=new DeliveryProof();proof.photoKey="key.png";when(proofs.findByCheckpointId(any())).thenReturn(Optional.of(proof));
        assertThat(service.submit(42L,parcelId,input(DeliveryStatus.FAILED,null,FailureReason.WRONG_ADDRESS),null,"token").syncStatus()).isEqualTo(SyncStatus.REJECTED);
        verify(proofs).delete(proof);verify(storage).discard("key.png");
    }
    @Test void aValidatedPhotoCanReplaceRecipientName() {
        var upload=mock(org.springframework.web.multipart.MultipartFile.class);
        when(storage.validate(upload)).thenReturn(new ProofStorageService.Photo(new byte[]{1},"png","digest"));
        when(storage.store(any())).thenReturn("private-photo.png");
        assertThat(service.submit(42L,parcelId,input(DeliveryStatus.DELIVERED,null,null),upload,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        verify(proofs).saveAndFlush(argThat(p->p.recipientName==null&&p.photoKey.equals("private-photo.png")));
    }
}
