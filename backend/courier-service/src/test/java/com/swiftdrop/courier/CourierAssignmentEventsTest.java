package com.swiftdrop.courier;

import com.swiftdrop.courier.config.CourierRabbitConfig;
import com.swiftdrop.courier.event.CourierDeliveryEvents;
import com.swiftdrop.courier.service.CourierDeliveryService;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"COURIER_DB_PASSWORD=unused","swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@Testcontainers(disabledWithoutDocker=true)
class CourierAssignmentEventsTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Container @ServiceConnection static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management")
        .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS","+S 2:2")
        .withStartupTimeout(Duration.ofMinutes(2));
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate template;
    @Autowired ObjectMapper mapper;
    @Autowired CourierDeliveryEvents consumer;
    @Autowired CourierDeliveryService deliveries;
    UUID owner=UUID.randomUUID(), other=UUID.randomUUID(), parcel=UUID.randomUUID();
    Instant at=Instant.now().minusSeconds(120);

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM courier_delivery_assignments");
        jdbc.update("DELETE FROM courier_delivery_parcels");
        jdbc.update("DELETE FROM courier_delivery_inbox");
        jdbc.update("DELETE FROM courier_profiles");
        profile(10,42,owner);profile(11,43,other);
    }
    void profile(long profile,long user,UUID dispatch) {
        jdbc.update("""
            INSERT INTO courier_profiles(id,user_id,dispatch_courier_id,name,phone,vehicle_type,vehicle_number,area,availability,active,created_at,updated_at,version)
            VALUES (?,?,?,'Test courier','0771234567','VAN','TEST','Colombo','AVAILABLE',true,now(),now(),0)
            """,profile,user,dispatch);
    }
    Map<String,Object> event(String type,Instant time) {
        var value=new HashMap<String,Object>();value.put("eventId",UUID.randomUUID());value.put("eventType",type);
        value.put("occurredAt",time);value.put("parcelId",parcel);value.put("trackingNumber","SD-RABBIT-TEST");return value;
    }
    Message message(String route,Map<String,Object> value) {
        var properties=new MessageProperties();properties.setContentType("application/json");properties.setReceivedRoutingKey(route);
        return new Message(mapper.writeValueAsBytes(value),properties);
    }
    void apply(String route,Map<String,Object> value) {consumer.receive(message(route,value));}
    void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(50);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    @Test void actualRabbitAssignmentAppearsWithSnapshotWithoutCallingOtherServices() throws Exception {
        var assignment=event("ParcelAssigned",at);assignment.put("courierId","10");assignment.put("courierUserId","42");assignment.put("assignedBy","7");
        assignment.put("parcel",Map.of("deliveryAddress","7 Hospital Road, Dehiwala","pickupAddress","Kandy",
            "receiverName","Demo Recipient","receiverPhone","0771234567","weight",2.5,"size","SMALL"));
        template.send(CourierRabbitConfig.EXCHANGE,CourierRabbitConfig.ASSIGNED,message(CourierRabbitConfig.ASSIGNED,assignment));
        await(()->jdbc.queryForObject("SELECT count(*) FROM courier_delivery_assignments",Integer.class)==1);
        assertThat(deliveries.detail(42L,parcel,"unused").parcel().receiverName()).isEqualTo("Demo Recipient");
        var snapshot=event("ParcelDeliverySnapshot",at.plusSeconds(1));
        snapshot.put("deliveryAddress","7 Hospital Road, Dehiwala");snapshot.put("pickupAddress","Kandy");
        snapshot.put("receiverName","Demo Recipient");snapshot.put("receiverPhone","0771234567");
        snapshot.put("weight",2.5);snapshot.put("size","SMALL");snapshot.put("deliveryDate",LocalDate.now(ZoneId.of("Asia/Colombo")));
        snapshot.put("pickupContactName","Demo Pickup");snapshot.put("pickupContactPhone","0771234567");
        snapshot.put("contentsDescription","Clothing");snapshot.put("lengthCm",25);snapshot.put("widthCm",20);snapshot.put("heightCm",10);
        snapshot.put("history",List.of(Map.of("id",UUID.randomUUID(),"status","CREATED","recordedAt",at.minusSeconds(10),
            "locationDescription","Kandy","attemptNo",1,"actorRole","CUSTOMER")));
        template.send(CourierRabbitConfig.EXCHANGE,CourierRabbitConfig.SNAPSHOT,message(CourierRabbitConfig.SNAPSHOT,snapshot));
        await(()->jdbc.queryForObject("SELECT count(*) FROM courier_delivery_parcels WHERE delivery_date IS NOT NULL",Integer.class)==1);
        var page=deliveries.deliveries(42L,"today",0,20,"unused");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().parcel().deliveryAddress()).contains("Hospital Road");
        assertThat(page.content().getFirst().parcel().weight()).isEqualByComparingTo("2.5");
        assertThat(page.content().getFirst().parcel().pickupContactName()).isEqualTo("Demo Pickup");
        assertThat(page.content().getFirst().parcel().lengthCm()).isEqualByComparingTo("25");
        assertThat(page.content().getFirst().parcel().history()).hasSize(1);
        assertThatThrownBy(()->deliveries.detail(43L,parcel,"unused")).hasMessageContaining("not assigned");
        apply(CourierRabbitConfig.ASSIGNED,assignment);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_delivery_inbox",Integer.class)).isEqualTo(2);
        assertThat(deliveries.deliveries(42L,"active",1,1,"unused").content()).isEmpty();
    }
    @Test void delayedAssignmentAndStatusCannotRestorePreviousOwnerOrRegressStatus() {
        var first=event("ParcelAssigned",at);first.put("courierId",owner);
        var changed=event("ParcelAssigned",at.plusSeconds(10));changed.put("courierId",other);
        apply(CourierRabbitConfig.ASSIGNED,changed);apply(CourierRabbitConfig.ASSIGNED,first);
        var delivered=event("ParcelStatusChanged",at.plusSeconds(30));delivered.put("oldStatus","OUT_FOR_DELIVERY");delivered.put("newStatus","DELIVERED");
        apply(CourierRabbitConfig.STATUS,delivered);
        var delayed=event("ParcelStatusChanged",at.plusSeconds(20));delayed.put("oldStatus","CREATED");delayed.put("newStatus","PICKED_UP");
        apply(CourierRabbitConfig.STATUS,delayed);
        assertThat(deliveries.deliveries(42L,"active",0,20,"unused").content()).isEmpty();
        assertThatThrownBy(()->deliveries.detail(42L,parcel,"unused")).hasMessageContaining("not assigned");
        assertThat(deliveries.deliveries(43L,"history",0,20,"unused").content()).hasSize(1);
        assertThat(deliveries.detail(43L,parcel,"unused").parcel().status()).isEqualTo("DELIVERED");
        assertThat(deliveries.detail(43L,parcel,"unused").parcel().history()).extracting(h->h.status()).containsExactly("PICKED_UP","DELIVERED");
    }
    @Test void invalidHistoryRollsBackSnapshotAndInboxTogether() {
        var invalid=event("ParcelDeliverySnapshot",at);
        invalid.put("deliveryAddress","Must not persist");
        invalid.put("history",List.of(Map.of("id",UUID.randomUUID(),"status","INVALID","recordedAt",at)));
        assertThatThrownBy(()->apply(CourierRabbitConfig.SNAPSHOT,invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_delivery_parcels",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_delivery_inbox",Integer.class)).isZero();
    }
    @Test void invalidEventsGoToCouriersOwnDeadLetterQueueWithoutAnInboxCommit() throws Exception {
        var invalid=event("ParcelStatusChanged",at);invalid.put("newStatus","UNSUPPORTED");invalid.put("oldStatus","CREATED");
        template.send(CourierRabbitConfig.EXCHANGE,CourierRabbitConfig.STATUS,message(CourierRabbitConfig.STATUS,invalid));
        var failed=template.receive(CourierRabbitConfig.QUEUE+".dlq",15000);
        assertThat(failed).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_delivery_inbox",Integer.class)).isZero();
    }
}
