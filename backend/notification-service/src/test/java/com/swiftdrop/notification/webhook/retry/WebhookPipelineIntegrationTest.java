package com.swiftdrop.notification.webhook.retry;

import com.swiftdrop.notification.webhook.consumer.WebhookParcelStatusConsumer;
import com.swiftdrop.notification.webhook.delivery.WebhookHttpSender;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real broker, database and HTTP receiver; settings lookup is an explicit test double. */
@Testcontainers(disabledWithoutDocker=true)
class WebhookPipelineIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Container static final GenericContainer<?> rabbit = new GenericContainer<>("rabbitmq:4-management")
            .withEnv("RABBITMQ_DEFAULT_USER","pipeline-test")
            .withEnv("RABBITMQ_DEFAULT_PASS","pipeline-test-password").withExposedPorts(5672);

    @Test void brokerToPersistenceToSignedRetryAndRecovery() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_webhook_delivery_records.sql")).execute(ds);
        var store = new WebhookDeliveryStore(new JdbcTemplate(ds),new DataSourceTransactionManager(ds));
        var mapper = new ObjectMapper();
        var consumer = new WebhookParcelStatusConsumer(mapper,new WebhookEventRecorder(store,mapper));
        var connection = new CachingConnectionFactory(rabbit.getHost(),rabbit.getMappedPort(5672));
        connection.setUsername("pipeline-test"); connection.setPassword("pipeline-test-password");
        var listener = new SimpleMessageListenerContainer(connection);
        var receiver = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger received = new AtomicInteger();
        AtomicInteger valid = new AtomicInteger();
        receiver.createContext("/webhook", exchange -> {
            byte[] raw = exchange.getRequestBody().readAllBytes();
            try {
                String stamp = exchange.getRequestHeaders().getFirst("X-SwiftDrop-Timestamp");
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec("pipeline-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                mac.update((stamp + ".").getBytes(StandardCharsets.UTF_8));
                String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(raw));
                if (expected.equals(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"))) valid.incrementAndGet();
                int attempt = received.incrementAndGet();
                exchange.sendResponseHeaders(attempt <= 2 ? 503 : 204,-1);
            } catch (Exception e) { exchange.sendResponseHeaders(500,-1); }
            finally { exchange.close(); }
        });
        receiver.start();
        try {
            var admin = new RabbitAdmin(connection);
            var queue = new org.springframework.amqp.core.Queue("webhook.parcel-status-changed");
            var exchange = new TopicExchange("swiftdrop.events");
            admin.declareQueue(queue); admin.declareExchange(exchange);
            admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("parcel.status-changed"));
            listener.setQueueNames(queue.getName());
            listener.setMessageListener((MessageListener) message -> {
                try { consumer.consume(message.getBody()); }
                catch (Exception e) { throw new IllegalStateException(e); }
            });
            listener.start();
            UUID event = UUID.randomUUID();
            byte[] payload = mapper.writeValueAsBytes(Map.of("eventId",event.toString(),"eventType","ParcelStatusChanged",
                    "occurredAt",Instant.now().toString(),"parcelId","parcel-1","customerId","user-1",
                    "merchantId","1","trackingNumber","SD-PIPELINE","oldStatus","CREATED","newStatus","DELIVERED"));
            var template = new RabbitTemplate(connection);
            template.send(exchange.getName(),"parcel.status-changed",new Message(payload));
            template.send(exchange.getName(),"parcel.status-changed",new Message(payload));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (store.history("1",false,0,20).total() == 0 && System.nanoTime() < deadline) Thread.sleep(50);
            var history = store.history("1",false,0,20);
            assertEquals(1,history.total());
            UUID id = history.items().getFirst().id();
            String origin = "http://127.0.0.1:" + receiver.getAddress().getPort();
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(1);
            for (int attempt=1; attempt<=3; attempt++) {
                Clock clock = Clock.fixed(now,ZoneOffset.UTC);
                var processor = new WebhookDeliveryProcessor(merchant -> Optional.of(
                        new MerchantWebhookSettingsLookup.Settings(URI.create(origin + "/webhook"),"pipeline-secret",true)),
                        new WebhookHttpSender(clock,Duration.ofSeconds(2),Duration.ofSeconds(5)),
                        new WebhookRetryPolicy(),clock,Set.of(origin));
                assertTrue(store.processOne(id,now,processor));
                var detail = store.detail("1",id).orElseThrow();
                assertEquals(attempt,detail.attemptCount());
                if (attempt < 3) {
                    assertEquals("RETRY_SCHEDULED",detail.state());
                    assertEquals(now.plusSeconds(attempt == 1 ? 60 : 300),detail.nextRetryAt());
                    now = detail.nextRetryAt();
                } else assertEquals("DELIVERED",detail.state());
            }
            assertEquals(3,valid.get());
            assertEquals(3,received.get());
            assertEquals(3,store.attempts("1",id).size());
            assertEquals(1,store.history("1",false,0,20).total());
        } finally { listener.stop(); connection.destroy(); receiver.stop(0); }
    }
}
