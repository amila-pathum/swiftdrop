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

    public record DeliverySummary(UUID id, UUID eventId, String trackingNumber,
            String merchantOrderReference, String parcelStatus, String state,
            int attemptCount, Instant createdAt, Instant nextRetryAt) { }
    public record Attempt(int attemptNumber, Instant attemptedAt, String outcome,
            Integer httpStatus, String failureCode, Instant nextRetryAt) { }
    public record Page(List<DeliverySummary> items, long total, int page, int size) { }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static DeliverySummary summary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new DeliverySummary(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getString("tracking_number"), rs.getString("merchant_order_reference"),
                rs.getString("status_sent"), rs.getString("state"), rs.getInt("attempt_count"),
                instant(rs, "created_at"), instant(rs, "next_attempt_at"));
    }
    public Page history(String merchantId, boolean failedOnly, int page, int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        String where = " WHERE merchant_id=?" + (failedOnly ? " AND state='FAILED'" : "");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM webhook_deliveries" + where, Long.class, merchantId);
        var items = jdbc.query("SELECT * FROM webhook_deliveries" + where
                + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?", WebhookDeliveryStore::summary,
                merchantId, size, (long) page * size);
        return new Page(items, total, page, size);
    }
    public java.util.Optional<DeliverySummary> detail(String merchantId, UUID id) {
        return jdbc.query("SELECT * FROM webhook_deliveries WHERE merchant_id=? AND id=?",
                WebhookDeliveryStore::summary, merchantId, id).stream().findFirst();
    }
    public List<Attempt> attempts(String merchantId, UUID id) {
        return jdbc.query("""
                SELECT a.* FROM webhook_delivery_attempts a
                JOIN webhook_deliveries d ON d.id=a.delivery_id
                WHERE d.merchant_id=? AND d.id=? ORDER BY a.attempt_number
                """, (rs, row) -> new Attempt(rs.getInt("attempt_number"), instant(rs,"attempted_at"),
                rs.getString("outcome"), rs.getObject("http_status", Integer.class),
                rs.getString("failure_code"), instant(rs,"next_retry_at")), merchantId, id);
    }
    /** Retain lifetime numbering; after the automatic budget, each manual retry grants one attempt. */
    public boolean retry(String merchantId, UUID id, Instant now) {
        return jdbc.update("""
                UPDATE webhook_deliveries SET state='PENDING', next_attempt_at=?, updated_at=?
                WHERE merchant_id=? AND id=? AND state='FAILED'
                """, Timestamp.from(now), Timestamp.from(now), merchantId, id) == 1;
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
