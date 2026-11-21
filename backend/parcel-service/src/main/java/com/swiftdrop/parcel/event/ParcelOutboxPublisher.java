package com.swiftdrop.parcel.event;

import com.swiftdrop.parcel.config.ParcelRabbitConfig;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "swiftdrop.events.publisher-enabled",
    havingValue = "true", matchIfMissing = true)
public class ParcelOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final long confirmTimeoutMs;

    public ParcelOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit,
            @Value("${swiftdrop.events.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        if (confirmTimeoutMs <= 0) throw new IllegalArgumentException("Confirm timeout must be positive");
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.events.publish-delay-ms:1000}",
        initialDelayString = "${swiftdrop.events.publish-initial-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<PendingEvent> events = jdbc.query("""
            SELECT event_id, routing_key, payload
              FROM parcel_event_outbox
             WHERE published_at IS NULL AND next_attempt_at <= now()
             ORDER BY created_at
             LIMIT 50
             FOR UPDATE SKIP LOCKED
            """, (rs, row) -> new PendingEvent(
                rs.getObject("event_id", UUID.class),
                rs.getString("routing_key"),
                rs.getString("payload")));

        for (PendingEvent event : events) {
            try {
                publishAndConfirm(event);
            } catch (InterruptedException exception) {
                recordFailure(event, "Interrupted while waiting for RabbitMQ confirmation");
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException | ExecutionException | TimeoutException exception) {
                String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
                recordFailure(event, message);
                continue;
            }
            jdbc.update("""
                UPDATE parcel_event_outbox
                   SET published_at = ?, attempts = attempts + 1, last_error = NULL
                 WHERE event_id = ?
                """, Timestamp.from(Instant.now()), event.eventId());
        }
    }

    private void publishAndConfirm(PendingEvent event)
            throws InterruptedException, ExecutionException, TimeoutException {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId().toString());
        var correlation = new CorrelationData(event.eventId().toString());
        rabbit.send(ParcelRabbitConfig.EXCHANGE, event.routingKey(),
            new Message(event.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8), properties), correlation);
        var confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
        if (!confirm.ack()) {
            throw new IllegalStateException("RabbitMQ rejected event: " + confirm.reason());
        }
        // Spring AMQP populates the correlated return before completing the confirm future.
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ returned unroutable event: "
                + correlation.getReturned().getReplyText());
        }
    }

    private void recordFailure(PendingEvent event, String message) {
        jdbc.update("""
                    UPDATE parcel_event_outbox
                       SET attempts = attempts + 1,
                           next_attempt_at = now() + interval '30 seconds',
                           last_error = ?
                     WHERE event_id = ?
                    """, message.substring(0, Math.min(message.length(), 1000)), event.eventId());
    }

    private record PendingEvent(UUID eventId, String routingKey, String payload) { }
}
