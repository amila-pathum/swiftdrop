package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ParcelStatusIdempotencyStore {
    private final JdbcTemplate jdbc;

    public ParcelStatusIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID key, UUID parcelId, Long actorId,
                         ParcelStatus requestedStatus, String requestedNote) {
        return jdbc.update("""
            INSERT INTO parcel_status_idempotency
                (idempotency_key, parcel_id, actor_id, requested_status, requested_note)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, key, parcelId, actorId, requestedStatus.name(), requestedNote) == 1;
    }

    public Optional<StoredResult> find(UUID key) {
        return jdbc.query("""
            SELECT parcel_id, actor_id, requested_status, requested_note,
                   previous_status, new_status, changed_at
              FROM parcel_status_idempotency
             WHERE idempotency_key = ?
            """, (rs, row) -> new StoredResult(
                rs.getObject("parcel_id", UUID.class),
                rs.getLong("actor_id"),
                ParcelStatus.valueOf(rs.getString("requested_status")),
                rs.getString("requested_note"),
                rs.getString("previous_status") == null ? null
                    : ParcelStatus.valueOf(rs.getString("previous_status")),
                rs.getString("new_status") == null ? null
                    : ParcelStatus.valueOf(rs.getString("new_status")),
                rs.getTimestamp("changed_at") == null ? null
                    : rs.getTimestamp("changed_at").toInstant()), key).stream().findFirst();
    }

    public void complete(UUID key, ParcelStatus previousStatus,
                         ParcelStatus newStatus, Instant changedAt) {
        jdbc.update("""
            UPDATE parcel_status_idempotency
               SET previous_status = ?, new_status = ?, changed_at = ?
             WHERE idempotency_key = ?
            """, previousStatus.name(), newStatus.name(), Timestamp.from(changedAt), key);
    }

    public record StoredResult(UUID parcelId, Long actorId,
                               ParcelStatus requestedStatus, String requestedNote,
                               ParcelStatus previousStatus, ParcelStatus newStatus,
                               Instant changedAt) { }
}
