package com.swiftdrop.dispatch;

import com.swiftdrop.dispatch.client.DispatchDataClient;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.*;
import com.swiftdrop.dispatch.event.DispatchEventPublisher;
import com.swiftdrop.dispatch.service.DispatchService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reproduces the existing RDS numeric schema without touching RDS. */
@SpringBootTest(properties={"spring.flyway.enabled=false","swiftdrop.dispatch.numeric-assignments=true",
    "spring.rabbitmq.listener.simple.auto-startup=false","DISPATCH_DB_PASSWORD=unused",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@Testcontainers(disabledWithoutDocker=true)
class NumericAssignmentCompatibilityTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @Autowired DispatchService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DispatchDataClient data;
    @MockitoBean DispatchEventPublisher publisher;
    UUID parcel=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
    @BeforeEach void setup() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS dispatch_assignments (
                assignment_id UUID PRIMARY KEY,parcel_id UUID NOT NULL,courier_profile_id BIGINT NOT NULL,
                courier_user_id BIGINT NOT NULL,courier_name VARCHAR(100) NOT NULL,previous_courier_profile_id BIGINT,
                tracking_number VARCHAR(40) NOT NULL,assigned_by VARCHAR(80) NOT NULL,assigned_at TIMESTAMPTZ NOT NULL,
                state VARCHAR(20) NOT NULL,version BIGINT NOT NULL,cancelled_by VARCHAR(80),cancelled_at TIMESTAMPTZ)
            """);
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_active ON dispatch_assignments(parcel_id) WHERE state='ACTIVE'");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS dispatch_report_events (
                event_id UUID PRIMARY KEY,event_type VARCHAR(80),occurred_at TIMESTAMPTZ,tracking_number VARCHAR(40),
                parcel_id VARCHAR(80),merchant_id VARCHAR(80),merchant_order_reference VARCHAR(150),customer_id VARCHAR(80),
                courier_id VARCHAR(80),old_status VARCHAR(30),new_status VARCHAR(30))
            """);
        jdbc.update("DELETE FROM dispatch_assignments");jdbc.update("DELETE FROM dispatch_report_events");
        var a=new CourierProfile(first,"First",true,true,10L,42L);
        var b=new CourierProfile(second,"Second",true,true,11L,43L);
        when(data.couriers()).thenReturn(List.of(a,b));when(data.findCourier(first)).thenReturn(Optional.of(a));
        when(data.findCourier(second)).thenReturn(Optional.of(b));
        when(data.findParcel(parcel)).thenReturn(Optional.of(new ParcelRow(parcel,"SD-NUMERIC-TEST","Kandy","Colombo", "Pickup","Delivery",
            "SMALL","CREATED",null,null,BigDecimal.TEN,Instant.now(),Instant.now(),new BigDecimal("2.5"),"Recipient","0771234567")));
    }
    @Test void persistsTrustedNumericIdentityAndReassignsWithoutLegacyProjectionTables() throws Exception {
        var assigned=service.assign(parcel,first,"7");
        verify(publisher).publishAssignment(argThat(event->event.parcel()!=null&&"Recipient".equals(event.parcel().receiverName())
            &&event.parcel().weight().compareTo(new BigDecimal("2.5"))==0));
        assertThat(assigned.courierId()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT courier_user_id FROM dispatch_assignments WHERE state='ACTIVE'",Long.class)).isEqualTo(42L);
        assertThat(service.couriers().getFirst().activeParcelCount()).isEqualTo(1);
        assertThat(service.assignedParcels(first)).hasSize(1);
        assertThatThrownBy(()->service.assign(parcel,first,"7")).hasMessageContaining("already assigned");
        service.assign(parcel,second,"7");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispatch_assignments WHERE state='CANCELLED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT previous_courier_profile_id FROM dispatch_assignments WHERE state='ACTIVE'",Long.class)).isEqualTo(10L);
        assertThat(service.assignedParcels(first)).isEmpty();assertThat(service.assignedParcels(second)).hasSize(1);
    }
    @Test void canonicalTerminalEventCompletesAssignmentAndDeduplicatesWithoutLegacyTables() {
        service.assign(parcel,first,"7");
        var event=new com.swiftdrop.dispatch.event.ParcelStatusChangedEvent(UUID.randomUUID(),"ParcelStatusChanged",
            Instant.now().plusSeconds(1),"SD-NUMERIC-TEST",parcel.toString(),null,null,"8","42","OUT_FOR_DELIVERY","DELIVERED");
        service.applyStatusEvent(event);service.applyStatusEvent(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispatch_report_events",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM dispatch_assignments",String.class)).isEqualTo("COMPLETED");
    }
}
