package com.swiftdrop.courier;

import com.swiftdrop.courier.config.CourierRabbitConfig;
import com.swiftdrop.courier.dto.CourierDtos.DeliveryInput;
import com.swiftdrop.courier.dto.CourierWorkflowDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.event.*;
import com.swiftdrop.courier.service.CourierWorkflowService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"COURIER_DB_PASSWORD=unused","swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "swiftdrop.events.publisher-enabled=false","spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class MerchantCourierWorkflowTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18").withDatabaseName("courier_db");
    @Container @ServiceConnection static final RabbitMQContainer broker=new RabbitMQContainer("rabbitmq:4-management")
        .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS","+S 2:2").withStartupTimeout(Duration.ofMinutes(2));
    static final String QUEUE="courier.workflow-test-events";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired RabbitTemplate rabbit;
    @Autowired PlatformTransactionManager manager;
    @Autowired CourierWorkflowService workflow;
    @Autowired CourierDeliveryEvents incoming;
    UUID parcel=UUID.randomUUID(),courierA=UUID.randomUUID(),courierB=UUID.randomUUID();

    @BeforeEach void setup() {
        for(String table:List.of("delivery_proofs","delivery_checkpoints","courier_assignment_requests","courier_delivery_assignments",
                "courier_delivery_parcels","courier_delivery_inbox","courier_event_outbox","courier_profiles"))jdbc.update("DELETE FROM "+table);
        profile(10,42,courierA,"Colombo","VAN","AVAILABLE",true);
        profile(11,43,courierB,"Galle","MOTORCYCLE","AVAILABLE",true);
        profile(12,44,UUID.randomUUID(),"Colombo","VAN","BUSY",true);
        profile(13,45,UUID.randomUUID(),"Colombo","VAN","OFFLINE",false);
        jdbc.update("""
            INSERT INTO courier_delivery_parcels(parcel_id,tracking_number,status,status_at,merchant_user_id,pickup_address,delivery_address,
                receiver_name,receiver_phone,weight,size) VALUES (?,'SD-MERCHANT-TEST','CREATED',now(),100,'Pickup desk','Recipient reception','Recipient','0771234567',2.5,'SMALL')
            """,parcel);
        jdbc.update("INSERT INTO courier_delivery_history(id,parcel_id,status,recorded_at,attempt_no,actor_role) VALUES (?,?,'CREATED',now(),1,'MERCHANT')",UUID.randomUUID(),parcel);
        var admin=new RabbitAdmin(rabbit.getConnectionFactory());
        admin.declareExchange(new TopicExchange(CourierRabbitConfig.EXCHANGE,true,false));
        admin.declareQueue(new Queue(QUEUE,true,false,false));
        admin.declareBinding(BindingBuilder.bind(new Queue(QUEUE)).to(new TopicExchange(CourierRabbitConfig.EXCHANGE)).with("courier.#"));
        admin.purgeQueue(QUEUE);
    }
    void profile(long id,long user,UUID reference,String area,String vehicle,String availability,boolean active) {
        jdbc.update("""
            INSERT INTO courier_profiles(id,user_id,dispatch_courier_id,name,phone,vehicle_type,vehicle_number,area,availability,active,created_at,updated_at,version)
            VALUES (?,?,?,'Courier','0771234567',?,'TEST',?,?,?,now(),now(),0)
            """,id,user,reference,vehicle,area,availability,active);
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor actor(String role,long id) {
        return jwt().jwt(j->j.subject(Long.toString(id)).claim("status","ACTIVE")).authorities(new SimpleGrantedAuthority("ROLE_"+role));
    }
    AssignmentInput request(long courier) {return new AssignmentInput(UUID.randomUUID(),parcel,courier,LocalDate.now(ZoneId.of("Asia/Colombo")));}
    AssignmentReceipt assign(AssignmentInput input) throws Exception {
        return mapper.readValue(mvc.perform(post("/api/couriers/assignments").with(actor("MERCHANT",100))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(),AssignmentReceipt.class);
    }
    DeliveryInput update(DeliveryStatus status) {
        return new DeliveryInput(UUID.randomUUID(),status,status==DeliveryStatus.DELIVERED?"Recipient":null,
            status==DeliveryStatus.FAILED?FailureReason.RECIPIENT_ABSENT:null,null,"Reception desk",null,null,null,null);
    }
    void progress(long user,DeliveryInput input) throws Exception {
        var route=input.status()==DeliveryStatus.IN_TRANSIT?patch("/api/couriers/me/deliveries/"+parcel+"/status"):post("/api/couriers/me/deliveries/"+parcel+"/status");
        mvc.perform(route.with(actor("COURIER",user)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.syncStatus").value("CONFIRMED"));
    }
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);}

    @Test void merchantDirectoryUsesActiveAvailableProfilesAndCouriersControlOwnAvailability() throws Exception {
        mvc.perform(get("/api/couriers/available").with(actor("MERCHANT",100)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(10))
            .andExpect(jsonPath("$.content[0].userId").doesNotExist());
        mvc.perform(get("/api/couriers/available").param("area","colombo").param("vehicleType","VAN").with(actor("MERCHANT",100)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for(String role:List.of("CUSTOMER","DISPATCHER","COURIER"))mvc.perform(get("/api/couriers/available").with(actor(role,42))).andExpect(status().isForbidden());
        mvc.perform(get("/api/couriers/available")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/couriers/available").param("size","1000").with(actor("MERCHANT",100))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/couriers/me/availability").with(actor("COURIER",42))).andExpect(status().isOk()).andExpect(jsonPath("$.availability").value("AVAILABLE"));
        for(int retry=0;retry<2;retry++)mvc.perform(patch("/api/couriers/me/availability").with(actor("COURIER",42)).contentType(MediaType.APPLICATION_JSON).content("{\"availability\":\"OFFLINE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.availability").value("OFFLINE"));
        assertThat(count("courier_event_outbox")).isEqualTo(1);
        mvc.perform(get("/api/couriers/10/availability").with(actor("MERCHANT",100))).andExpect(status().isOk()).andExpect(jsonPath("$.availability").value("OFFLINE"));
        mvc.perform(post("/api/couriers/assignments").with(actor("MERCHANT",100)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(10))))
            .andExpect(status().isConflict());
        mvc.perform(patch("/api/couriers/11/active").with(actor("MERCHANT",100)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/couriers/11/active").with(actor("ADMIN",9)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.availability").value("OFFLINE"));
        assertThat(count("courier_event_outbox")).isEqualTo(2);
    }

    @Test void merchantAssignmentAndFullCourierLifecycleAreAtomicIdempotentAndPublishActualEvents() throws Exception {
        var input=request(10);var result=assign(input);assertThat(assign(input).assignmentId()).isEqualTo(result.assignmentId());
        assertThat(count("courier_assignment_requests")).isEqualTo(1);assertThat(count("courier_event_outbox")).isEqualTo(1);
        mvc.perform(post("/api/couriers/assignments").with(actor("MERCHANT",200)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isForbidden());
        var changed=new AssignmentInput(input.submissionId(),parcel,11L,input.deliveryDate());
        mvc.perform(post("/api/couriers/assignments").with(actor("MERCHANT",100)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(changed))).andExpect(status().isConflict());
        mvc.perform(get("/api/couriers/merchant/deliveries/"+parcel).with(actor("MERCHANT",200))).andExpect(status().isForbidden());
        mvc.perform(get("/api/couriers/me/deliveries/"+parcel).with(actor("COURIER",43))).andExpect(status().isForbidden());
        var picked=update(DeliveryStatus.PICKED_UP);progress(42,picked);progress(42,picked);
        assertThat(count("courier_event_outbox")).isEqualTo(2);
        mvc.perform(post("/api/couriers/me/deliveries/"+parcel+"/status").with(actor("COURIER",42)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(update(DeliveryStatus.DELIVERED))))
            .andExpect(status().isConflict());
        progress(42,update(DeliveryStatus.IN_TRANSIT));progress(42,update(DeliveryStatus.OUT_FOR_DELIVERY));progress(42,update(DeliveryStatus.DELIVERED));
        mvc.perform(get("/api/couriers/merchant/deliveries/"+parcel+"/status").with(actor("MERCHANT",100))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(get("/api/couriers/me/deliveries").param("filter","active").with(actor("COURIER",42))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/couriers/merchant/deliveries").param("status","DELIVERED").with(actor("MERCHANT",100))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(count("courier_delivery_history")).isEqualTo(5);assertThat(count("delivery_proofs")).isEqualTo(1);
        assertThat(count("courier_event_outbox")).isEqualTo(5);
        new CourierOutboxPublisher(jdbc,rabbit,manager).publishDue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_event_outbox WHERE published_at IS NOT NULL",Long.class)).isEqualTo(5);
        var types=new HashSet<String>();
        for(int i=0;i<5;i++) {
            var message=rabbit.receive(QUEUE,5000);assertThat(message).isNotNull();
            var event=mapper.readValue(message.getBody(),Map.class);types.add(event.get("eventType").toString());
            assertThat(((Number)event.get("merchantUserId")).longValue()).isEqualTo(100L);
            assertThat(event).doesNotContainKeys("password","token","photoKey","latitude","longitude");
        }
        assertThat(types).containsExactlyInAnyOrder("CourierDeliveryAssigned","CourierDeliveryStatusChanged");
    }

    @Test void reassignmentRevokesPreviousCourierAndCancellationIsMerchantOwnedAndBeforePickupOnly() throws Exception {
        assign(request(10));assign(request(11));
        mvc.perform(get("/api/couriers/me/deliveries/"+parcel).with(actor("COURIER",42))).andExpect(status().isForbidden());
        var cancel=new CancelInput(UUID.randomUUID(),"Customer cancelled before pickup");
        mvc.perform(post("/api/couriers/assignments/"+parcel+"/cancel").with(actor("MERCHANT",200)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(cancel))).andExpect(status().isForbidden());
        for(int i=0;i<2;i++)mvc.perform(post("/api/couriers/assignments/"+parcel+"/cancel").with(actor("MERCHANT",100)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(cancel)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(count("courier_event_outbox")).isEqualTo(3);
        mvc.perform(post("/api/couriers/me/deliveries/"+parcel+"/status").with(actor("COURIER",43)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(update(DeliveryStatus.PICKED_UP))))
            .andExpect(status().isForbidden());
    }

    @Test void failedDeliveryCanStartANewMerchantAuthorizedAttemptWithoutOverwritingProofs() throws Exception {
        assign(request(10));progress(42,update(DeliveryStatus.PICKED_UP));progress(42,update(DeliveryStatus.IN_TRANSIT));
        progress(42,update(DeliveryStatus.OUT_FOR_DELIVERY));progress(42,update(DeliveryStatus.FAILED));
        var second=assign(request(11));assertThat(second.attemptNo()).isEqualTo(2);assertThat(second.status()).isEqualTo("CREATED");
        progress(43,update(DeliveryStatus.PICKED_UP));progress(43,update(DeliveryStatus.IN_TRANSIT));
        progress(43,update(DeliveryStatus.OUT_FOR_DELIVERY));progress(43,update(DeliveryStatus.DELIVERED));
        assertThat(jdbc.queryForList("SELECT attempt_no FROM delivery_proofs ORDER BY attempt_no",Integer.class)).containsExactly(1,2);
    }

    @Test void concurrentIdenticalAssignmentsCreateOneReceiptAndOneOutboxEvent() throws Exception {
        var input=request(10);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->workflow.assign(100L,input));var b=executor.submit(()->workflow.assign(100L,input));
            assertThat(a.get(15,TimeUnit.SECONDS).assignmentId()).isEqualTo(b.get(15,TimeUnit.SECONDS).assignmentId());
        }
        assertThat(count("courier_assignment_requests")).isEqualTo(1);assertThat(count("courier_event_outbox")).isEqualTo(1);
    }

    @Test void unroutedRabbitEventRemainsPendingAndCanBeRetriedAfterASubscriberBinds() throws Exception {
        assign(request(10));var admin=new RabbitAdmin(rabbit.getConnectionFactory());admin.deleteQueue(QUEUE);
        var publisher=new CourierOutboxPublisher(jdbc,rabbit,manager);publisher.publishDue();
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM courier_event_outbox",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempts FROM courier_event_outbox",Integer.class)).isEqualTo(1);
        admin.declareQueue(new Queue(QUEUE,true,false,false));
        admin.declareBinding(BindingBuilder.bind(new Queue(QUEUE)).to(new TopicExchange(CourierRabbitConfig.EXCHANGE)).with("courier.#"));
        jdbc.update("UPDATE courier_event_outbox SET next_attempt_at=now()");publisher.publishDue();
        assertThat(rabbit.receive(QUEUE,5000)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM courier_event_outbox",Boolean.class)).isTrue();
    }

    @Test void ownershipCannotBeClaimedByRequestAndLegacyEventsCannotOverwriteLocallyManagedState() throws Exception {
        mvc.perform(post("/api/couriers/assignments").with(actor("MERCHANT",200)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"submissionId\":\""+UUID.randomUUID()+"\",\"parcelId\":\""+parcel+"\",\"courierId\":10,\"merchantUserId\":100}"))
            .andExpect(status().isForbidden());
        assign(request(10));
        var envelope=new HashMap<String,Object>();envelope.put("eventId",UUID.randomUUID());envelope.put("eventType","ParcelAssigned");
        envelope.put("occurredAt",Instant.now().plusSeconds(60));envelope.put("parcelId",parcel);envelope.put("trackingNumber","SD-MERCHANT-TEST");envelope.put("courierId",courierB);
        apply(CourierRabbitConfig.ASSIGNED,envelope);
        envelope.put("eventId",UUID.randomUUID());envelope.put("eventType","ParcelStatusChanged");envelope.put("oldStatus","OUT_FOR_DELIVERY");envelope.put("newStatus","DELIVERED");
        apply(CourierRabbitConfig.STATUS,envelope);
        assertThat(workflow.courierActivity(42L,parcel).status()).isEqualTo("CREATED");
        assertThat(count("courier_delivery_history")).isEqualTo(1);
        jdbc.update("UPDATE courier_delivery_parcels SET merchant_user_id=NULL WHERE parcel_id=?",parcel);
        mvc.perform(post("/api/couriers/assignments").with(actor("MERCHANT",100)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(11)))).andExpect(status().isConflict());
    }
    void apply(String route,Map<String,Object> event) {
        var properties=new MessageProperties();properties.setReceivedRoutingKey(route);properties.setContentType("application/json");
        incoming.receive(new Message(mapper.writeValueAsBytes(event),properties));
    }
    @Test void trustedParcelEventsLearnMerchantIdentityButCannotReplaceIt() throws Exception {
        jdbc.update("UPDATE courier_delivery_parcels SET merchant_user_id=NULL WHERE parcel_id=?",parcel);
        var event=new HashMap<String,Object>();event.put("eventId",UUID.randomUUID());event.put("eventType","ParcelStatusChanged");
        event.put("occurredAt",Instant.now());event.put("parcelId",parcel);event.put("trackingNumber","SD-MERCHANT-TEST");
        event.put("oldStatus",null);event.put("newStatus","CREATED");event.put("merchantId","100");
        apply(CourierRabbitConfig.STATUS,event);
        assertThat(jdbc.queryForObject("SELECT merchant_user_id FROM courier_delivery_parcels WHERE parcel_id=?",Long.class,parcel)).isEqualTo(100L);
        assign(request(10));
        event.put("eventId",UUID.randomUUID());event.put("eventType","ParcelDeliverySnapshot");event.put("merchantUserId",200L);
        event.put("receiverName","Must not overwrite");
        assertThatThrownBy(()->apply(CourierRabbitConfig.SNAPSHOT,event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT receiver_name FROM courier_delivery_parcels WHERE parcel_id=?",String.class,parcel)).isEqualTo("Recipient");
    }
    @Test void manualSqlExampleIsIdempotentAndDoesNotClaimAnotherMerchantsExistingRow() throws Exception {
        postgres.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath("demo/manual_merchant_example.sql"),"/tmp/courier-manual-example.sql");
        UUID imported=UUID.randomUUID();
        for(int i=0;i<2;i++)assertThat(importExample(imported,100).getExitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_delivery_history WHERE parcel_id=?",Long.class,imported)).isEqualTo(1);
        assertThat(importExample(imported,200).getExitCode()).isNotZero();
        assertThat(jdbc.queryForObject("SELECT merchant_user_id FROM courier_delivery_parcels WHERE parcel_id=?",Long.class,imported)).isEqualTo(100L);
    }
    org.testcontainers.containers.Container.ExecResult importExample(UUID id,long merchant) throws Exception {
        return postgres.execInContainer("psql","-U",postgres.getUsername(),"-d",postgres.getDatabaseName(),"-v","merchant_user_id="+merchant,
            "-v","parcel_id="+id,"-v","tracking_number=SD-MANUAL-EXAMPLE","-f","/tmp/courier-manual-example.sql");
    }
}
