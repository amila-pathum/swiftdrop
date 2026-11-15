package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.ActiveDeliveriesByCourier;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CountByStatus;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryByDay;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.StatusHistoryRow;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dispatch's own data only: {@code dispatch_assignments} and the stored parcel
 * status events in {@code dispatch_report_events}.
 */
@Repository
public class DispatchRepository {
    /** Statuses in which a parcel is out with its courier. */
    public static final List<String> IN_PROGRESS_STATUSES = List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY");

    /** The parcel's latest stored status, or CREATED when no event has arrived yet. */
    private static final String LATEST_STATUS_JOIN = """
        LEFT JOIN LATERAL (
            SELECT e.new_status FROM dispatch_report_events e
             WHERE e.parcel_id = a.parcel_id::text
             ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1
        ) latest ON true
        """;

    private static final String ASSIGNMENT_ROW_SELECT = """
        SELECT a.assignment_id, a.parcel_id, a.tracking_number, a.courier_profile_id,
               a.courier_name, a.assigned_by, a.assigned_at, a.state,
               COALESCE(latest.new_status, 'CREATED') AS parcel_status
          FROM dispatch_assignments a
        """ + LATEST_STATUS_JOIN;

    private final NamedParameterJdbcTemplate jdbc;

    public DispatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- assignments

    public Set<UUID> activeAssignedParcelIds() {
        return new HashSet<>(jdbc.query(
            "SELECT parcel_id FROM dispatch_assignments WHERE state = 'ACTIVE'",
            Map.of(), (rs, row) -> rs.getObject("parcel_id", UUID.class)));
    }

    public Optional<ActiveAssignment> activeAssignment(UUID parcelId) {
        return jdbc.query("""
            SELECT assignment_id, tracking_number, courier_profile_id, courier_user_id, courier_name,
                   assigned_by, assigned_at
              FROM dispatch_assignments WHERE parcel_id = :parcelId AND state = 'ACTIVE'
            """, Map.of("parcelId", parcelId), DispatchRepository::mapActive).stream().findFirst();
    }

    /** Active assignment count per courier profile id. */
    public Map<Long, Long> activeAssignmentCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        jdbc.query("""
            SELECT courier_profile_id, count(*) AS active_count
              FROM dispatch_assignments WHERE state = 'ACTIVE' GROUP BY courier_profile_id
            """, Map.of(), rs -> {
                counts.put(rs.getLong("courier_profile_id"), rs.getLong("active_count"));
            });
        return counts;
    }

    /**
     * Inserts a new ACTIVE assignment. The unique partial index on
     * (parcel_id) WHERE state = 'ACTIVE' rejects a second active row.
     */
    public AssignmentResult insertAssignment(UUID parcelId, String trackingNumber, Long courierProfileId,
            Long courierUserId, String courierName, Long previousCourierProfileId,
            String assignedBy, Instant assignedAt) {
        UUID assignmentId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO dispatch_assignments
                (assignment_id, parcel_id, tracking_number, courier_profile_id, courier_user_id,
                 courier_name, previous_courier_profile_id, assigned_by, assigned_at, state)
            VALUES (:assignmentId, :parcelId, :trackingNumber, :courierProfileId, :courierUserId,
                 :courierName, :previousCourierProfileId, :assignedBy, :assignedAt, 'ACTIVE')
            """, new MapSqlParameterSource()
                .addValue("assignmentId", assignmentId)
                .addValue("parcelId", parcelId)
                .addValue("trackingNumber", trackingNumber)
                .addValue("courierProfileId", courierProfileId)
                .addValue("courierUserId", courierUserId)
                .addValue("courierName", courierName)
                .addValue("previousCourierProfileId", previousCourierProfileId)
                .addValue("assignedBy", assignedBy)
                .addValue("assignedAt", Timestamp.from(assignedAt)));
        return new AssignmentResult(assignmentId, parcelId, trackingNumber, courierProfileId, courierName,
            previousCourierProfileId, assignedBy, assignedAt, "ACTIVE");
    }

    /** Cancels the parcel's ACTIVE assignment, if any, and returns it as it was. */
    public Optional<ActiveAssignment> cancelActiveAssignment(UUID parcelId, String cancelledBy, Instant cancelledAt) {
        return jdbc.query("""
            UPDATE dispatch_assignments
               SET state = 'CANCELLED', cancelled_by = :cancelledBy, cancelled_at = :cancelledAt,
                   version = version + 1
             WHERE parcel_id = :parcelId AND state = 'ACTIVE'
            RETURNING assignment_id, tracking_number, courier_profile_id, courier_user_id, courier_name,
                      assigned_by, assigned_at
            """, new MapSqlParameterSource("cancelledBy", cancelledBy)
                .addValue("cancelledAt", Timestamp.from(cancelledAt))
                .addValue("parcelId", parcelId), DispatchRepository::mapActive).stream().findFirst();
    }

    public long countAssignments(String state, Long courierId, String trackingPattern) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = assignmentWhere(state, courierId, trackingPattern, params);
        Long total = jdbc.queryForObject("SELECT count(*) FROM dispatch_assignments a" + where, params, Long.class);
        return total == null ? 0 : total;
    }

    public List<AssignmentRow> findAssignments(String state, Long courierId, String trackingPattern,
            int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit).addValue("offset", offset);
        String where = assignmentWhere(state, courierId, trackingPattern, params);
        return jdbc.query(ASSIGNMENT_ROW_SELECT + where
            + " ORDER BY a.assigned_at DESC, a.assignment_id LIMIT :limit OFFSET :offset",
            params, DispatchRepository::mapAssignmentRow);
    }

    public List<AssignmentRow> parcelAssignments(UUID parcelId) {
        return jdbc.query(ASSIGNMENT_ROW_SELECT + " WHERE a.parcel_id = :parcelId ORDER BY a.assigned_at, a.assignment_id",
            Map.of("parcelId", parcelId), DispatchRepository::mapAssignmentRow);
    }

    /**
     * Every assignment of a courier profile, with its attempt number among all
     * assignments of the same parcel.
     */
    public List<CourierAssignmentRecord> courierAssignments(Long courierProfileId) {
        return jdbc.query("""
            SELECT assignment_id, parcel_id, courier_profile_id, courier_user_id, state, version,
                   assigned_at, attempt_no
              FROM (
                  SELECT a.*, row_number() OVER (
                             PARTITION BY a.parcel_id ORDER BY a.assigned_at, a.assignment_id) AS attempt_no
                    FROM dispatch_assignments a
                   WHERE a.parcel_id IN (
                       SELECT parcel_id FROM dispatch_assignments WHERE courier_profile_id = :courierId)
              ) numbered
             WHERE courier_profile_id = :courierId
             ORDER BY assigned_at, assignment_id
            """, Map.of("courierId", courierProfileId), (rs, row) -> new CourierAssignmentRecord(
                rs.getObject("assignment_id", UUID.class), rs.getObject("parcel_id", UUID.class),
                rs.getLong("courier_profile_id"), rs.getLong("courier_user_id"),
                rs.getString("state"), rs.getLong("version"),
                rs.getTimestamp("assigned_at").toInstant(), rs.getInt("attempt_no")));
    }

    // ---------------------------------------------------------------- events

    /**
     * Stores a parcel status event once per event id and applies it to the
     * parcel's active assignment. Returns false when the event id was already stored.
     */
    public boolean applyStatusEvent(ParcelStatusChangedEvent event) {
        int inserted = jdbc.update("""
            INSERT INTO dispatch_report_events
                (event_id, event_type, occurred_at, tracking_number, parcel_id, merchant_id,
                 merchant_order_reference, customer_id, courier_id, old_status, new_status)
            VALUES (:eventId, :eventType, :occurredAt, :trackingNumber, :parcelId, :merchantId,
                 :merchantOrderReference, :customerId, :courierId, :oldStatus, :newStatus)
            ON CONFLICT (event_id) DO NOTHING
            """, new MapSqlParameterSource()
                .addValue("eventId", event.eventId())
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                .addValue("trackingNumber", event.trackingNumber())
                .addValue("parcelId", event.parcelId())
                .addValue("merchantId", event.merchantId())
                .addValue("merchantOrderReference", event.merchantOrderReference())
                .addValue("customerId", event.customerId())
                .addValue("courierId", event.courierId())
                .addValue("oldStatus", event.oldStatus())
                .addValue("newStatus", event.newStatus()));
        if (inserted == 0) return false;
        UUID parcelId = UUID.fromString(event.parcelId());
        if ("DELIVERED".equals(event.newStatus()) || "FAILED".equals(event.newStatus())) {
            // version is deliberately unchanged: courier-service rejects a retried
            // submission when the assignment version differs.
            jdbc.update("""
                UPDATE dispatch_assignments SET state = 'COMPLETED'
                 WHERE parcel_id = :parcelId AND state = 'ACTIVE'
                """, Map.of("parcelId", parcelId));
        } else if ("CANCELLED".equals(event.newStatus())) {
            jdbc.update("""
                UPDATE dispatch_assignments
                   SET state = 'CANCELLED', cancelled_by = 'parcel-event', cancelled_at = :occurredAt
                 WHERE parcel_id = :parcelId AND state = 'ACTIVE'
                """, new MapSqlParameterSource("parcelId", parcelId)
                    .addValue("occurredAt", Timestamp.from(event.occurredAt())));
        }
        return true;
    }

    public List<StatusHistoryRow> history(UUID parcelId) {
        return jdbc.query("""
            SELECT old_status, new_status, occurred_at FROM dispatch_report_events
             WHERE parcel_id = :parcelId ORDER BY occurred_at, event_id
            """, Map.of("parcelId", parcelId.toString()), (rs, row) -> new StatusHistoryRow(
                rs.getString("old_status"), rs.getString("new_status"),
                rs.getTimestamp("occurred_at").toInstant()));
    }

    // ---------------------------------------------------------------- deliveries

    private static final String LATEST_EVENTS = """
        WITH latest AS (
            SELECT DISTINCT ON (parcel_id) parcel_id, tracking_number, new_status, occurred_at
              FROM dispatch_report_events
             ORDER BY parcel_id, occurred_at DESC, event_id DESC
        )
        """;

    public long countDeliveries(Collection<String> statuses, String trackingPattern) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long total = jdbc.queryForObject(LATEST_EVENTS + "SELECT count(*) FROM latest l"
            + deliveryWhere(statuses, trackingPattern, params), params, Long.class);
        return total == null ? 0 : total;
    }

    public List<DeliveryRow> findDeliveries(Collection<String> statuses, String trackingPattern,
            int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(LATEST_EVENTS + """
            SELECT l.parcel_id, l.tracking_number, l.new_status, l.occurred_at,
                   a.courier_profile_id, a.courier_name
              FROM latest l
              LEFT JOIN LATERAL (
                  SELECT courier_profile_id, courier_name FROM dispatch_assignments a
                   WHERE a.parcel_id::text = l.parcel_id
                   ORDER BY a.assigned_at DESC, a.assignment_id DESC LIMIT 1
              ) a ON true
            """ + deliveryWhere(statuses, trackingPattern, params)
            + " ORDER BY l.occurred_at DESC, l.parcel_id LIMIT :limit OFFSET :offset",
            params, (rs, row) -> new DeliveryRow(
                UUID.fromString(rs.getString("parcel_id")), rs.getString("tracking_number"),
                rs.getString("new_status"), (Long) rs.getObject("courier_profile_id", Long.class),
                rs.getString("courier_name"), rs.getTimestamp("occurred_at").toInstant()));
    }

    // ---------------------------------------------------------------- reports

    public List<DeliveryByDay> summaryDeliveriesPerDay(LocalDate from, LocalDate toInclusive) {
        return jdbc.query("""
            SELECT (days.day AT TIME ZONE 'UTC')::date AS report_date,
                   count(e.event_id) FILTER (WHERE e.new_status = 'DELIVERED') AS delivered,
                   count(e.event_id) FILTER (WHERE e.new_status = 'FAILED') AS failed
              FROM generate_series(
                  CAST(CAST(:from AS date) AS timestamp) AT TIME ZONE 'UTC',
                  CAST(CAST(:to AS date) AS timestamp) AT TIME ZONE 'UTC',
                  interval '1 day') days(day)
              LEFT JOIN dispatch_report_events e
                ON e.occurred_at >= days.day AND e.occurred_at < days.day + interval '1 day'
               AND e.new_status IN ('DELIVERED', 'FAILED')
             GROUP BY days.day ORDER BY days.day
            """, new MapSqlParameterSource("from", from).addValue("to", toInclusive),
            (rs, row) -> {
                long delivered = rs.getLong("delivered");
                long failed = rs.getLong("failed");
                return new DeliveryByDay(rs.getDate("report_date").toLocalDate(), delivered, failed,
                    delivered + failed);
            });
    }

    public List<CountByStatus> summaryCountsByStatus(Instant from, Instant to) {
        return jdbc.query("""
            SELECT new_status AS status, count(*) AS total
              FROM dispatch_report_events
             WHERE occurred_at >= :from AND occurred_at < :to
             GROUP BY new_status ORDER BY new_status
            """, range(from, to), (rs, row) -> new CountByStatus(
                rs.getString("status"), rs.getLong("total")));
    }

    /**
     * Parcels whose latest status before {@code asOf} is PICKED_UP, IN_TRANSIT or
     * OUT_FOR_DELIVERY, grouped by the courier named in that event.
     */
    public List<ActiveDeliveriesByCourier> summaryActiveByCourier(Instant asOf) {
        return jdbc.query("""
            WITH latest AS (
                SELECT DISTINCT ON (parcel_id) parcel_id, courier_id, new_status
                  FROM dispatch_report_events
                 WHERE occurred_at < :asOf
                 ORDER BY parcel_id, occurred_at DESC, event_id DESC
            ), active AS (
                SELECT courier_id, count(*) AS total
                  FROM latest
                 WHERE courier_id IS NOT NULL
                   AND new_status IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY')
                 GROUP BY courier_id
            )
            SELECT active.courier_id, active.total, c.courier_profile_id, c.courier_name
              FROM active
              LEFT JOIN LATERAL (
                  SELECT courier_profile_id, courier_name FROM dispatch_assignments a
                   WHERE a.courier_user_id::text = active.courier_id
                   ORDER BY a.assigned_at DESC LIMIT 1
              ) c ON true
             ORDER BY active.total DESC, active.courier_id
            """, Map.of("asOf", Timestamp.from(asOf)), (rs, row) -> new ActiveDeliveriesByCourier(
                (Long) rs.getObject("courier_profile_id", Long.class), rs.getString("courier_id"),
                rs.getString("courier_name"), rs.getLong("total")));
    }

    // ---------------------------------------------------------------- helpers

    public record ActiveAssignment(UUID assignmentId, String trackingNumber, Long courierProfileId,
            Long courierUserId, String courierName, String assignedBy, Instant assignedAt) { }

    public record CourierAssignmentRecord(UUID assignmentId, UUID parcelId, Long courierProfileId,
            Long courierUserId, String state, long version, Instant assignedAt, int attemptNo) {
        public LocalDate deliveryDate() {
            return assignedAt.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    private static String assignmentWhere(String state, Long courierId, String trackingPattern,
            MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (state != null) {
            where.append(" AND a.state = :state");
            params.addValue("state", state);
        }
        if (courierId != null) {
            where.append(" AND a.courier_profile_id = :courierId");
            params.addValue("courierId", courierId);
        }
        if (trackingPattern != null) {
            where.append(" AND a.tracking_number ILIKE :tracking ESCAPE '!'");
            params.addValue("tracking", trackingPattern);
        }
        return where.toString();
    }

    private static String deliveryWhere(Collection<String> statuses, String trackingPattern,
            MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE l.new_status IN (:statuses)");
        params.addValue("statuses", statuses);
        if (trackingPattern != null) {
            where.append(" AND l.tracking_number ILIKE :tracking ESCAPE '!'");
            params.addValue("tracking", trackingPattern);
        }
        return where.toString();
    }

    private static MapSqlParameterSource range(Instant from, Instant to) {
        return new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
    }

    private static ActiveAssignment mapActive(ResultSet rs, int row) throws SQLException {
        return new ActiveAssignment(rs.getObject("assignment_id", UUID.class), rs.getString("tracking_number"),
            rs.getLong("courier_profile_id"), rs.getLong("courier_user_id"), rs.getString("courier_name"),
            rs.getString("assigned_by"), rs.getTimestamp("assigned_at").toInstant());
    }

    private static AssignmentRow mapAssignmentRow(ResultSet rs, int row) throws SQLException {
        return new AssignmentRow(rs.getObject("assignment_id", UUID.class), rs.getObject("parcel_id", UUID.class),
            rs.getString("tracking_number"), rs.getLong("courier_profile_id"), rs.getString("courier_name"),
            rs.getString("assigned_by"), rs.getTimestamp("assigned_at").toInstant(), rs.getString("state"),
            rs.getString("parcel_status"));
    }
}
