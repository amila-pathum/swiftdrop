package com.swiftdrop.parcel.event;

import com.swiftdrop.parcel.config.ParcelRabbitConfig;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
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

    public ParcelOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
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
                rabbit.convertAndSend(ParcelRabbitConfig.EXCHANGE,
                    event.routingKey(), event.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                jdbc.update("""
                    UPDATE parcel_event_outbox
                       SET published_at = ?, attempts = attempts + 1, last_error = NULL
                     WHERE event_id = ?
                    """, Timestamp.from(Instant.now()), event.eventId());
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
                jdbc.update("""
                    UPDATE parcel_event_outbox
                       SET attempts = attempts + 1,
                           next_attempt_at = now() + interval '30 seconds',
                           last_error = ?
                     WHERE event_id = ?
                    """, message.substring(0, Math.min(message.length(), 1000)), event.eventId());
            }
        }
    }

    private record PendingEvent(UUID eventId, String routingKey, String payload) { }
}
