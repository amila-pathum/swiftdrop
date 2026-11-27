package com.swiftdrop.courier.service;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.photo.DeliveryPhotoService;
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
    DeliveryPhotoService photos;CourierDeliveryService service;CourierProfile courier;
    UUID parcelId;Assignment assignment;Map<UUID,DeliveryCheckpoint> operations;
    @BeforeEach void setup() {
        profiles=mock(CourierProfileService.class);dispatch=mock(DispatchClient.class);parcels=mock(ParcelClient.class);
        checkpoints=mock(DeliveryCheckpointRepository.class);proofs=mock(DeliveryProofRepository.class);photos=mock(DeliveryPhotoService.class);
        var manager=new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object tx,TransactionDefinition definition){}
            protected void doCommit(DefaultTransactionStatus status){}
            protected void doRollback(DefaultTransactionStatus status){}
        };
        service=new CourierDeliveryService(profiles,dispatch,parcels,checkpoints,proofs,photos,manager);
        courier=new CourierProfile();courier.id=10L;courier.userId=42L;courier.active=true;
        parcelId=UUID.randomUUID();assignment=new Assignment(UUID.randomUUID(),parcelId,10L,1,true,0,LocalDate.now(),Instant.now());
        when(profiles.operational(42L)).thenReturn(courier);when(dispatch.assignments(10L,"token")).thenReturn(List.of(assignment));
        when(parcels.parcel(parcelId,"token")).thenReturn(new ParcelDetails(parcelId,"SD1","OUT_FOR_DELIVERY","Collection place","Delivery place","Recipient","0700000000"));
        operations=new HashMap<>();when(checkpoints.findById(any())).thenAnswer(i->Optional.ofNullable(operations.get(i.getArgument(0))));
        when(checkpoints.lockById(any())).thenAnswer(i->Optional.ofNullable(operations.get(i.getArgument(0))));
        when(checkpoints.saveAndFlush(any())).thenAnswer(i->{DeliveryCheckpoint c=i.getArgument(0);operations.put(c.id,c);return c;});
    }
    DeliveryInput input(DeliveryStatus status,String name,FailureReason reason) {
        return input(status,name,reason,null);
    }
    DeliveryInput input(DeliveryStatus status,String name,FailureReason reason,String photoKey) {
        return new DeliveryInput(UUID.randomUUID(),status,name,reason,null,"Front gate",null,null,null,null,photoKey);
    }
    @Test void todayIncludesUnscheduledNewAssignmentsWithoutInventingDeliveryDates() {
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Colombo"));
        Instant received=today.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant().plusSeconds(1);
        when(dispatch.assignments(10L,"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,10L,1,true,0,null,received)));
        var page=service.deliveries(42L,"today",0,20,"token");
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().assignment().deliveryDate()).isNull();
        when(dispatch.assignments(10L,"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,10L,1,true,0,today.plusDays(1),received)));
        assertThat(service.deliveries(42L,"today",0,20,"token").content()).isEmpty();
    }
    @Test void rejectsOtherCouriersAssignmentsBeforeReadingOrUpdatingParcel() {
        when(dispatch.assignments(10L,"token")).thenReturn(List.of());
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.DELIVERED,"Recipient",null),"token"))
            .isInstanceOf(CourierException.class).hasMessageContaining("not assigned");
        verifyNoInteractions(parcels);verify(proofs,never()).saveAndFlush(any());
    }
    @Test void rejectsUpstreamAssignmentsForAnotherCourierRatherThanTrustingTheirIds() {
        when(dispatch.assignments(10L,"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,99L,1,true,0,LocalDate.now(),Instant.now())));
        assertThatThrownBy(()->service.detail(42L,parcelId,"token")).isInstanceOf(RemoteFailure.class);
        verifyNoInteractions(parcels);
    }
    @Test void deliveredNeedsRecipientNameEvenWithPhotoAndFailedNeedsReason() {
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.DELIVERED," ",null),"token")).hasMessageContaining("recipient name");
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.DELIVERED,null,null,"proofs/p/photo.png"),"token")).hasMessageContaining("recipient name");
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.PICKED_UP,"Someone",null),"token")).hasMessageContaining("only for DELIVERED");
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.FAILED,null,null),"token")).hasMessageContaining("requires a reason");
        verifyNoInteractions(dispatch,parcels);
    }
    @Test void confirmsEvidenceOnlyAfterRemoteSuccessAndRetriesAreIdempotent() {
        var input=input(DeliveryStatus.DELIVERED,"Recipient",null);
        assertThat(service.submit(42L,parcelId,input,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        assertThat(service.submit(42L,parcelId,input,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        verify(parcels,times(1)).update(eq(parcelId),any(),eq("token"));verify(proofs,times(1)).saveAndFlush(argThat(p->p.attemptNo==1&&p.collectionAddressSnapshot.equals("Collection place")&&p.deliveryAddressSnapshot.equals("Delivery place")));
    }
    @Test void timeoutRetainsEvidenceButDoesNotClaimDeliveryAndLeasePreventsImmediateRetry() {
        doThrow(new RemoteFailure("Parcel service",false)).when(parcels).update(any(),any(),any());
        var input=input(DeliveryStatus.FAILED,null,FailureReason.RECIPIENT_ABSENT);
        assertThat(service.submit(42L,parcelId,input,"token").syncStatus()).isEqualTo(SyncStatus.UNKNOWN);
        service.submit(42L,parcelId,input,"token");verify(parcels,times(1)).update(any(),any(),any());
        assertThat(operations.get(input.submissionId()).leaseUntil).isAfter(Instant.now());
    }
    @Test void retryCannotChangePreviouslySubmittedContentOrUseAnotherCouriersSubmission() {
        var input=input(DeliveryStatus.DELIVERED,"Recipient",null);service.submit(42L,parcelId,input,"token");
        var changed=new DeliveryInput(input.submissionId(),input.status(),"Different",null,null,"Front gate",null,null,null,null,null);
        assertThatThrownBy(()->service.submit(42L,parcelId,changed,"token")).hasMessageContaining("different content");
        var other=new CourierProfile();other.id=99L;other.userId=43L;other.active=true;when(profiles.operational(43L)).thenReturn(other);
        assertThatThrownBy(()->service.submit(43L,parcelId,input,"token")).hasMessageContaining("another courier");
    }
    @Test void rejectedOutcomeReleasesFinalProofAndDeletesItsPhoto() {
        doThrow(new RemoteFailure("Parcel service",true)).when(parcels).update(any(),any(),any());
        var proof=new DeliveryProof();when(proofs.findByCheckpointId(any())).thenReturn(Optional.of(proof));
        var input=input(DeliveryStatus.FAILED,null,FailureReason.WRONG_ADDRESS,"proofs/p/gate.jpg");
        assertThat(service.submit(42L,parcelId,input,"token").syncStatus()).isEqualTo(SyncStatus.REJECTED);
        verify(proofs).delete(proof);
        verify(photos).deleteFile("proofs/p/gate.jpg");
        assertThat(operations.get(input.submissionId()).photoKey).isNull();
    }
    @Test void anyStageCanCarryAVerifiedPhotoThatIsUsedOnce() {
        var input=input(DeliveryStatus.PICKED_UP,null,null,"proofs/p/pickup.png");
        assertThat(service.submit(42L,parcelId,input,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        verify(photos).verify(10L,parcelId,"proofs/p/pickup.png");
        verify(photos).markUsed("proofs/p/pickup.png");
        assertThat(operations.get(input.submissionId()).photoKey).isEqualTo("proofs/p/pickup.png");
        verify(proofs,never()).saveAndFlush(any());
        // An identical retry does not check or claim the photo a second time.
        service.submit(42L,parcelId,input,"token");
        verify(photos,times(1)).verify(any(),any(),any());verify(photos,times(1)).markUsed(any());
    }
    @Test void aPhotoThatFailsVerificationStopsTheUpdate() {
        doThrow(CourierException.invalid("The file is not a supported image.")).when(photos).verify(10L,parcelId,"proofs/p/fake.png");
        assertThatThrownBy(()->service.submit(42L,parcelId,input(DeliveryStatus.IN_TRANSIT,null,null,"proofs/p/fake.png"),"token"))
            .hasMessageContaining("not a supported image");
        verify(checkpoints,never()).saveAndFlush(any());verify(parcels,never()).update(any(),any(),any());
    }
}
