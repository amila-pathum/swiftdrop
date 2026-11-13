package com.swiftdrop.notification.webhook.retry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class WebhookDeliveryStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public WebhookDeliveryStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(20);
    }

    public UUID enqueue(UUID eventId, String merchantId, String trackingNumber,
            String reference, String status, byte[] payload, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO webhook_deliveries
            (id,event_id,merchant_id,tracking_number,merchant_order_reference,status_sent,
             payload,state,attempt_count,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,'PENDING',0,?,?,?)
            ON CONFLICT (event_id,merchant_id) DO NOTHING
            """, id,eventId,merchantId,trackingNumber,reference,status,payload,
                Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
        return jdbc.queryForObject("""
            SELECT id FROM webhook_deliveries WHERE event_id=? AND merchant_id=?
            """, UUID.class, eventId, merchantId);
    }

    public List<UUID> due(Instant now, int limit) {
        return jdbc.query("""
            SELECT id FROM webhook_deliveries
            WHERE state IN ('PENDING','RETRY_SCHEDULED') AND next_attempt_at <= ?
            ORDER BY next_attempt_at, id LIMIT ?
            """, (rs,row) -> rs.getObject("id",UUID.class), Timestamp.from(now),limit);
    }

    public boolean processOne(UUID id, Instant now, WebhookDeliveryProcessor processor) {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            var rows = jdbc.query("""
                SELECT id,event_id,merchant_id,payload,attempt_count FROM webhook_deliveries
                WHERE id=? AND state IN ('PENDING','RETRY_SCHEDULED')
                  AND next_attempt_at <= ? FOR UPDATE SKIP LOCKED
                """, (rs,row) -> new WebhookDelivery(
                    rs.getObject("id",UUID.class), rs.getObject("event_id",UUID.class),
                    rs.getString("merchant_id"), rs.getBytes("payload"),
                    rs.getInt("attempt_count")), id,Timestamp.from(now));
            if (rows.isEmpty()) return false;
            WebhookDelivery delivery = rows.getFirst();
            WebhookDeliveryProcessor.Outcome result;
            try {
                result = processor.attempt(delivery);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Webhook worker interrupted", exception);
            }
            int attempt = delivery.attemptCount()+1;
            Timestamp next = result.nextAttemptAt() == null ? null
                    : Timestamp.from(result.nextAttemptAt());
            jdbc.update("""
                INSERT INTO webhook_delivery_attempts
                (delivery_id,attempt_number,attempted_at,outcome,http_status,failure_code,next_retry_at)
                VALUES (?,?,?,?,?,?,?)
                """, id,attempt,Timestamp.from(result.attemptedAt()),result.state(),
                    result.httpStatus(),result.failureCode(),next);
            jdbc.update("""
                UPDATE webhook_deliveries SET state=?, attempt_count=?,
                    next_attempt_at=?, updated_at=? WHERE id=?
                """, result.state(),attempt,next,Timestamp.from(result.attemptedAt()),id);
            return true;
        }));
    }
}
