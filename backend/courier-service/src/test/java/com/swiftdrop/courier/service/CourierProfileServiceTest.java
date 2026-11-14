package com.swiftdrop.courier.service;
import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.repository.CourierProfileRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierProfileServiceTest {
    @Test void provisioningVerifiesRoleAndIsIdempotent() {
        var repo=mock(CourierProfileRepository.class);var identity=mock(IdentityClient.class);var service=new CourierProfileService(repo,identity);
        var input=new ProvisionInput(42L,new ProfileInput("Courier","0771234567",VehicleType.VAN,"ABC1234","Colombo"));
        when(identity.account(42L,"token")).thenReturn(new IdentityAccount(42L,"CUSTOMER","ACTIVE"));
        assertThatThrownBy(()->service.provision(input,"token")).hasMessageContaining("COURIER role");
        verify(repo,never()).saveAndFlush(any());
        when(identity.account(42L,"token")).thenReturn(new IdentityAccount(42L,"COURIER","ACTIVE"));
        var existing=new CourierProfile();existing.id=10L;existing.userId=42L;existing.active=true;
        when(repo.findByUserId(42L)).thenReturn(Optional.of(existing));
        assertThat(service.provision(input,"token").created()).isFalse();verify(repo,never()).saveAndFlush(any());
    }
    @Test void inactiveCouriersCannotGoAvailableAndDeactivationForcesOffline() {
        var repo=mock(CourierProfileRepository.class);var service=new CourierProfileService(repo,mock(IdentityClient.class));
        var p=new CourierProfile();p.id=10L;p.userId=42L;p.active=false;
        when(repo.findByUserId(42L)).thenReturn(Optional.of(p));
        assertThatThrownBy(()->service.availability(42L,Availability.AVAILABLE)).hasMessageContaining("inactive");
        p.active=true;p.availability=Availability.BUSY;when(repo.findById(10L)).thenReturn(Optional.of(p));when(repo.saveAndFlush(p)).thenReturn(p);
        var result=service.active(10L,false,"token");assertThat(result.active()).isFalse();assertThat(result.availability()).isEqualTo(Availability.OFFLINE);
    }
}
