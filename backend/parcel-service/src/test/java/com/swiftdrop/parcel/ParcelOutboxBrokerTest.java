package com.swiftdrop.parcel;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.event.ParcelOutboxPublisher;
import com.swiftdrop.parcel.service.ParcelCreationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"PARCEL_DB_PASSWORD=test", "swiftdrop.events.publisher-enabled=true",
    "swiftdrop.events.publish-initial-delay-ms=3600000",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@Testcontainers
@org.springframework.test.context.TestPropertySource(locations = "file:src/main/resources/application.properties")
class ParcelOutboxBrokerTest {
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
    @Autowired ParcelCreationService creation;
    @Autowired ParcelOutboxPublisher publisher;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PricingClient pricing;
    RabbitAdmin admin;
    String queue;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE merchant_order_idempotency,parcel_event_outbox,parcel_status_history,parcels CASCADE");
        when(pricing.quote(any(), anyString())).thenReturn(new BigDecimal("450.00"));
        creation.createCustomerParcel(new CreateParcelRequest("Receiver", "0771234567", "Pickup", null,
            "Colombo", "Delivery", null, "Kandy", BigDecimal.ONE, "SMALL", "STANDARD"), 42L, "token");
        admin = new RabbitAdmin(rabbit);
        admin.declareExchange(new TopicExchange("swiftdrop.events", true, false));
        queue = "outbox-test-" + java.util.UUID.randomUUID();
        admin.declareQueue(new Queue(queue, true));
    }
    @AfterEach void cleanup() { if (admin != null && queue != null) admin.deleteQueue(queue); }
    void bind() { admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE,
        "swiftdrop.events", "parcel.status-changed", null)); }

    @Test void acknowledgedDeliveryMarksPublishedAndCarriesOriginalPayload() {
        bind();
        publisher.publishPending();
        assertTrue(published());
        Message received = rabbit.receive(queue, 5000);
        assertNotNull(received);
        assertEquals(jdbc.queryForObject("SELECT payload FROM parcel_event_outbox", String.class),
            new String(received.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(MessageDeliveryMode.PERSISTENT, received.getMessageProperties().getReceivedDeliveryMode());
        publisher.publishPending();
        assertNull(rabbit.receive(queue));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM parcel_event_outbox", Integer.class));
    }

    @Test void unroutableEventRemainsPendingThenRecoversWhenBindingIsAdded() {
        publisher.publishPending();
        assertFalse(published());
        assertTrue(jdbc.queryForObject("SELECT last_error FROM parcel_event_outbox", String.class).contains("unroutable"));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > now() FROM parcel_event_outbox", Boolean.class));
        publisher.publishPending(); // Backoff excludes the failed event.
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM parcel_event_outbox", Integer.class));
        bind();
        jdbc.update("UPDATE parcel_event_outbox SET next_attempt_at = now()");
        publisher.publishPending();
        assertTrue(published());
        assertNotNull(rabbit.receive(queue, 5000));
        assertEquals(2, jdbc.queryForObject("SELECT attempts FROM parcel_event_outbox", Integer.class));
        assertNull(jdbc.queryForObject("SELECT last_error FROM parcel_event_outbox", String.class));
    }

    @Test void brokenConnectionRetainsEventAndHealthyConnectionCanRetry() {
        bind();
        var connection = new CachingConnectionFactory("127.0.0.1", 1);
        connection.setConnectionTimeout(500);
        try {
            new ParcelOutboxPublisher(jdbc, new RabbitTemplate(connection), 100).publishPending();
            assertFalse(published());
        } finally { connection.destroy(); }
        jdbc.update("UPDATE parcel_event_outbox SET next_attempt_at = now()");
        publisher.publishPending();
        assertTrue(published());
        assertNotNull(rabbit.receive(queue, 5000));
    }
    boolean published() {
        return jdbc.queryForObject("SELECT published_at IS NOT NULL FROM parcel_event_outbox", Boolean.class);
    }
}
