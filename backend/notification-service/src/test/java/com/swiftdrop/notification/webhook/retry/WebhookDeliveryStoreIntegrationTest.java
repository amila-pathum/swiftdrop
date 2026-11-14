package com.swiftdrop.notification.webhook.retry;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses only an isolated PostgreSQL container, never local or shared RDS data. */
@Testcontainers(disabledWithoutDocker=true)
class WebhookDeliveryStoreIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18");
    private JdbcTemplate jdbc;
    private WebhookDeliveryStore store;
    private final Instant now=Instant.parse("2026-11-13T00:00:00Z");
    private final byte[] payload="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeEach void setUp() {
        var dataSource=new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS webhook_delivery_attempts");
        jdbc.execute("DROP TABLE IF EXISTS webhook_deliveries");
        new ResourceDatabasePopulator(new ClassPathResource(
                "webhook/create_webhook_delivery_records.sql"))
                .execute(dataSource);
        store=new WebhookDeliveryStore(jdbc,new DataSourceTransactionManager(dataSource));
    }
    private UUID enqueue(UUID event) {
        return store.enqueue(event,"m1","SD-TEST","SHOP-1","DELIVERED",payload,now);
    }
    @Test void historyAndRetryAreIsolatedAndKeepAttemptNumbers() throws Exception {
        UUID id = enqueue(UUID.randomUUID());
        assertTrue(store.history("other", false, 0, 20).items().isEmpty());
        assertTrue(store.detail("other", id).isEmpty());
        assertFalse(store.retry("m1", id, now)); // Pending cannot be manually retried.
        var processor = mock(WebhookDeliveryProcessor.class);
        when(processor.attempt(any())).thenReturn(new WebhookDeliveryProcessor.Outcome(
                "FAILED", now, null, 500, "HTTP_ERROR"));
        store.processOne(id, now, processor);
        assertFalse(store.retry("other", id, now));
        assertTrue(store.retry("m1", id, now));
        assertFalse(store.retry("m1", id, now)); // Repeated click cannot enqueue again.
        when(processor.attempt(any())).thenReturn(new WebhookDeliveryProcessor.Outcome(
                "DELIVERED", now, null, 204, null));
        store.processOne(id, now, processor);
        assertEquals(List.of(1,2), store.attempts("m1", id).stream()
                .map(WebhookDeliveryStore.Attempt::attemptNumber).toList());
        assertTrue(store.attempts("other", id).isEmpty());
        assertFalse(store.retry("m1", id, now));
        assertEquals(0, store.history("m1", true, 0, 20).total());
    }
    @Test void duplicateEventCreatesOnlyOneDelivery() {
        UUID event=UUID.randomUUID();
        assertEquals(enqueue(event),enqueue(event));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM webhook_deliveries",Integer.class));
    }
    @Test void failurePersistsHistoryAndFutureSchedule() throws Exception {
        UUID id=enqueue(UUID.randomUUID());
        var processor=mock(WebhookDeliveryProcessor.class);
        when(processor.attempt(any())).thenReturn(new WebhookDeliveryProcessor.Outcome(
                "RETRY_SCHEDULED",now,now.plusSeconds(60),500,"HTTP_ERROR"));
        assertTrue(store.processOne(id,now,processor));
        assertTrue(store.due(now,10).isEmpty());
        assertEquals(List.of(id),store.due(now.plusSeconds(60),10));
        assertEquals(1,jdbc.queryForObject(
                "SELECT count(*) FROM webhook_delivery_attempts WHERE delivery_id=?",Integer.class,id));
        // New store instance simulates restarting the application: schedule is still in DB.
        var recovered=new WebhookDeliveryStore(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()));
        assertEquals(List.of(id),recovered.due(now.plusSeconds(60),10));
    }
    @Test void deliveredRecordCannotBeProcessedAgain() throws Exception {
        UUID id=enqueue(UUID.randomUUID());
        var processor=mock(WebhookDeliveryProcessor.class);
        when(processor.attempt(any())).thenReturn(new WebhookDeliveryProcessor.Outcome(
                "DELIVERED",now,null,204,null));
        assertTrue(store.processOne(id,now,processor));
        assertFalse(store.processOne(id,now.plusSeconds(600),processor));
        verify(processor,times(1)).attempt(any());
        assertTrue(store.due(now.plusSeconds(600),10).isEmpty());
    }
    @Test void fifthFailureStaysFailedAndKeepsAllAttempts() throws Exception {
        UUID id=enqueue(UUID.randomUUID());
        var processor=mock(WebhookDeliveryProcessor.class);
        for (int attempt=1;attempt<=5;attempt++) {
            Instant time=now.plusSeconds(attempt);
            when(processor.attempt(any())).thenReturn(new WebhookDeliveryProcessor.Outcome(
                    attempt==5?"FAILED":"RETRY_SCHEDULED",time,
                    attempt==5?null:time.plusSeconds(1),500,"HTTP_ERROR"));
            assertTrue(store.processOne(id,time,processor));
        }
        assertEquals("FAILED",jdbc.queryForObject(
                "SELECT state FROM webhook_deliveries WHERE id=?",String.class,id));
        assertEquals(5,jdbc.queryForObject(
                "SELECT count(*) FROM webhook_delivery_attempts WHERE delivery_id=?",Integer.class,id));
        assertTrue(store.due(now.plusSeconds(600),10).isEmpty());
    }
}
