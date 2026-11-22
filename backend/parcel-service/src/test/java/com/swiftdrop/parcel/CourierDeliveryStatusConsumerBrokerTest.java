package com.swiftdrop.parcel;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.repository.ParcelRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** A courier's confirmed update, published exactly as courier-service does, moves the parcel. */
@SpringBootTest(properties = {"PARCEL_DB_PASSWORD=test",
    "swiftdrop.events.courier-status-consumer-enabled=true",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@Testcontainers
class CourierDeliveryStatusConsumerBrokerTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Container
    static final GenericContainer<?> broker = new GenericContainer<>("rabbitmq:3.13-alpine")
        .withEnv("RABBITMQ_DEFAULT_USER", "test").withEnv("RABBITMQ_DEFAULT_PASS", "test")
        .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", broker::getHost);
        registry.add("spring.rabbitmq.port", () -> broker.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;
    Parcel parcel;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE merchant_order_idempotency,parcel_event_outbox,parcel_status_history,parcels CASCADE");
        Parcel p = new Parcel();
        p.setTrackingNumber("SD" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        p.setSenderId(42L);
        p.setCreatedBy(42L);
        p.setReceiverName("Nimal Perera");
        p.setReceiverPhone("0771234567");
        p.setPickupAddress("10 Main Street, Colombo");
        p.setDeliveryAddress("20 Lake Road, Kandy");
        p.setWeight(new BigDecimal("2.500"));
        p.setSize("MEDIUM");
        p.setParcelType("STANDARD");
        p.setCharge(new BigDecimal("450.00"));
        p.setStatus(ParcelStatus.CREATED);
        p.setAssignedCourierId(88L);
        parcel = parcels.saveAndFlush(p);
    }

    /** Payload shape of courier-service CourierWorkflowService.changeStatus + CourierOutbox.save. */
    private void publish(UUID submissionId, long courierUserId, String oldStatus, String newStatus) {
        String json = """
            {"eventId":"%s","eventType":"CourierDeliveryStatusChanged","schemaVersion":1,"occurredAt":"%s",
             "submissionId":"%s","parcelId":"%s","trackingNumber":"%s","merchantUserId":null,"courierId":3,
             "courierUserId":%d,"assignmentId":"%s","assignmentVersion":0,"attemptNo":1,
             "oldStatus":"%s","newStatus":"%s","changedAt":"%s"}
            """.formatted(UUID.randomUUID(), Instant.now(), submissionId, parcel.getId(), parcel.getTrackingNumber(),
                courierUserId, UUID.randomUUID(), oldStatus, newStatus, Instant.now());
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send("swiftdrop.events", "courier.delivery-status-changed.v1",
            new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) throw new AssertionError("Condition not met in time");
            Thread.sleep(100);
        }
    }

    private ParcelStatus status() {
        return parcels.findById(parcel.getId()).orElseThrow().getStatus();
    }

    @Test void pickedUpByAssignedCourierUpdatesParcelHistoryAndPublishesStatusChange() throws Exception {
        UUID submission = UUID.randomUUID();

        publish(submission, 88L, "CREATED", "PICKED_UP");

        await(() -> status() == ParcelStatus.PICKED_UP);
        assertThat(jdbc.queryForObject("""
            SELECT changed_by FROM parcel_status_history WHERE parcel_id = ? AND new_status = 'PICKED_UP'
            """, Long.class, parcel.getId())).isEqualTo(88L);
        // parcel.status-changed, which notifies the customer, uses the courier's submission id.
        String payload = jdbc.queryForObject("""
            SELECT payload FROM parcel_event_outbox WHERE event_id = ? AND routing_key = 'parcel.status-changed'
            """, String.class, submission);
        assertThat(payload).contains("\"customerId\":\"42\"").contains("\"oldStatus\":\"CREATED\"")
            .contains("\"newStatus\":\"PICKED_UP\"");

        // The same update delivered again changes nothing.
        publish(submission, 88L, "CREATED", "PICKED_UP");
        publish(UUID.randomUUID(), 77L, "PICKED_UP", "IN_TRANSIT"); // another courier: ignored
        UUID next = UUID.randomUUID();
        publish(next, 88L, "PICKED_UP", "IN_TRANSIT");
        await(() -> status() == ParcelStatus.IN_TRANSIT);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM parcel_status_history WHERE parcel_id = ?", Integer.class, parcel.getId()))
            .isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT changed_by FROM parcel_status_history WHERE parcel_id = ? AND new_status = 'IN_TRANSIT'",
            Long.class, parcel.getId())).isEqualTo(88L);
    }
}
