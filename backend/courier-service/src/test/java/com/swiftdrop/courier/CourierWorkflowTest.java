package com.swiftdrop.courier;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.repository.*;
import com.swiftdrop.courier.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"COURIER_DB_PASSWORD=unused","swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@Testcontainers(disabledWithoutDocker=true)
class CourierWorkflowTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @Autowired CourierProfileService profiles;
    @Autowired CourierDeliveryService deliveries;
    @Autowired CourierProfileRepository profileRepo;
    @Autowired DeliveryCheckpointRepository checkpointRepo;
    @Autowired DeliveryProofRepository proofRepo;
    @MockitoBean IdentityClient identity;
    @MockitoBean DispatchClient dispatch;
    @MockitoBean ParcelClient parcels;
    @BeforeEach void resetData(){proofRepo.deleteAll();checkpointRepo.deleteAll();profileRepo.deleteAll();}
    Profile create(long userId) {
        when(identity.account(eq(userId),anyString())).thenReturn(new IdentityAccount(userId,"COURIER","ACTIVE"));
        return profiles.provision(new ProvisionInput(userId,new ProfileInput("Courier "+userId,"0771234567",VehicleType.VAN,"ABC1234","Colombo")),"token").profile();
    }
    @Test void persistsPickupDeliveryAndSeparateFailedReattemptWithoutOverwritingHistory() {
        var courier=create(42L);UUID parcelId=UUID.randomUUID();
        when(dispatch.assignments(courier.id(),"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,courier.id(),1,true,0,LocalDate.now(),Instant.now())));
        when(parcels.parcel(parcelId,"token")).thenReturn(new ParcelDetails(parcelId,"SD1","OUT_FOR_DELIVERY","Pickup address","Delivery address","Recipient","0771234567"));
        var pickup=new DeliveryInput(UUID.randomUUID(),DeliveryStatus.PICKED_UP,null,null,null,"Collected at front desk",null,null,null,null);
        assertThat(deliveries.submit(42L,parcelId,pickup,null,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        var failed=new DeliveryInput(UUID.randomUUID(),DeliveryStatus.FAILED,null,FailureReason.RECIPIENT_ABSENT,null,"Destination gate",null,null,null,null);
        deliveries.submit(42L,parcelId,failed,null,"token");
        when(dispatch.assignments(courier.id(),"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,courier.id(),2,true,0,LocalDate.now(),Instant.now())));
        var success=new DeliveryInput(UUID.randomUUID(),DeliveryStatus.DELIVERED,"Recipient",null,null,"Reception",null,null,null,null);
        deliveries.submit(42L,parcelId,success,null,"token");
        assertThat(deliveries.proofs(42L,parcelId,"token")).extracting(Proof::outcome).containsExactly(DeliveryStatus.FAILED,DeliveryStatus.DELIVERED);
        assertThat(deliveries.tracking(42L,parcelId,"token")).hasSize(3);
        assertThat(deliveries.proofs(42L,parcelId,"token").get(1).collectionAddressSnapshot()).isEqualTo("Pickup address");
        assertThat(deliveries.submit(42L,parcelId,success,null,"token").syncStatus()).isEqualTo(SyncStatus.CONFIRMED);
        verify(parcels,times(3)).update(eq(parcelId),any(),eq("token"));
    }
    @Test void anotherCourierCannotReadTrackingProofsOrSubmission() {
        var owner=create(42L);var other=create(43L);UUID parcelId=UUID.randomUUID();
        when(dispatch.assignments(owner.id(),"token")).thenReturn(List.of(new Assignment(UUID.randomUUID(),parcelId,owner.id(),1,true,0,LocalDate.now(),Instant.now())));
        when(dispatch.assignments(other.id(),"token")).thenReturn(List.of());
        when(parcels.parcel(parcelId,"token")).thenReturn(new ParcelDetails(parcelId,"SD1","OUT_FOR_DELIVERY","Pickup","Destination","Recipient","0771234567"));
        var request=new DeliveryInput(UUID.randomUUID(),DeliveryStatus.DELIVERED,"Recipient",null,null,"Reception",null,null,null,null);
        deliveries.submit(42L,parcelId,request,null,"token");
        assertThatThrownBy(()->deliveries.tracking(43L,parcelId,"token")).hasMessageContaining("not assigned");
        assertThatThrownBy(()->deliveries.proofs(43L,parcelId,"token")).hasMessageContaining("not assigned");
        assertThatThrownBy(()->deliveries.submission(43L,request.submissionId())).hasMessageContaining("another courier");
    }
}
