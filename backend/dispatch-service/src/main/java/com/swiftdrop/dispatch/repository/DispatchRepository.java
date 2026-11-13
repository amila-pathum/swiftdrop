package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CountByStatus;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ActiveDeliveriesByCourier;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignedParcel;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierPerformance;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierProfile;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryByDay;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.FailedDelivery;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.RouteRevenue;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.StatusHistoryRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.VolumeByPeriod;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashMap;

@Repository
public class DispatchRepository {
    private static final String PARCEL_SELECT = """
        SELECT p.parcel_id, p.tracking_number, p.pickup_area, p.dropoff_area,
               p.pickup_address, p.delivery_address, p.size, p.status,
               p.assigned_courier_id, c.display_name AS courier_name, p.charge,
               p.created_at, p.updated_at
          FROM dispatch_parcels p
          LEFT JOIN dispatch_couriers c ON c.courier_id = p.assigned_courier_id
        """;
    private final NamedParameterJdbcTemplate jdbc;

    public DispatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ParcelRow> findParcels(String where, SqlParameterSource parameters, String orderBy,
                                      int limit, long offset) {
        MapSqlParameterSource paged = new MapSqlParameterSource();
        if (parameters instanceof MapSqlParameterSource source) {
            source.getValues().forEach(paged::addValue);
        }
        paged.addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(PARCEL_SELECT + where + " ORDER BY " + orderBy
            + " LIMIT :limit OFFSET :offset", paged, this::mapParcel);
    }

    public long countParcels(String where, SqlParameterSource parameters) {
        return jdbc.queryForObject("SELECT count(*) FROM dispatch_parcels p " + where,
            parameters, Long.class);
    }

    public Optional<ParcelRow> findParcel(UUID id) {
        List<ParcelRow> rows = jdbc.query(PARCEL_SELECT + " WHERE p.parcel_id = :id",
            Map.of("id", id), this::mapParcel);
        return rows.stream().findFirst();
    }

    public List<ParcelRow> createdParcels() {
        return jdbc.query(PARCEL_SELECT + " WHERE p.status = 'CREATED' ORDER BY p.created_at, p.parcel_id",
            Map.of(), this::mapParcel);
    }

    public Optional<ParcelRow> lockParcel(UUID id) {
        List<ParcelRow> rows = jdbc.query(PARCEL_SELECT + " WHERE p.parcel_id = :id FOR UPDATE OF p",
            Map.of("id", id), this::mapParcel);
        return rows.stream().findFirst();
    }

    public List<StatusHistoryRow> history(UUID id) {
        return jdbc.query("""
            SELECT previous_status, new_status, changed_at, changed_by, note
              FROM dispatch_status_history WHERE parcel_id = :id ORDER BY changed_at, history_id
            """, Map.of("id", id), (rs, row) -> new StatusHistoryRow(
                rs.getString("previous_status"), rs.getString("new_status"),
                rs.getTimestamp("changed_at").toInstant(), rs.getString("changed_by"),
                rs.getString("note")));
    }

    public List<CourierProfile> courierProfiles() {
        return jdbc.query("""
            SELECT courier_id, display_name, active, available
              FROM dispatch_couriers WHERE role = 'COURIER' ORDER BY display_name
            """, Map.of(), (rs, row) -> new CourierProfile(
                rs.getObject("courier_id", UUID.class), rs.getString("display_name"),
                rs.getBoolean("active"), rs.getBoolean("available")));
    }

    public Optional<CourierProfile> findCourier(UUID id) {
        List<CourierProfile> rows = jdbc.query("""
            SELECT courier_id, display_name, active, available
              FROM dispatch_couriers WHERE courier_id = :id AND role = 'COURIER'
            """, Map.of("id", id), (rs, row) -> new CourierProfile(
                rs.getObject("courier_id", UUID.class), rs.getString("display_name"),
                rs.getBoolean("active"), rs.getBoolean("available")));
        return rows.stream().findFirst();
    }

    public boolean hasActiveAssignment(UUID parcelId) {
        Integer matches = jdbc.queryForObject("""
            SELECT count(*) FROM dispatch_assignments
             WHERE parcel_id = :parcelId AND state = 'ACTIVE'
            """, Map.of("parcelId", parcelId), Integer.class);
        return matches != null && matches > 0;
    }

    public Optional<ActiveAssignment> activeAssignment(UUID parcelId) {
        List<ActiveAssignment> rows = jdbc.query("""
            SELECT assignment_id, courier_id, assigned_by, assigned_at
              FROM dispatch_assignments WHERE parcel_id = :parcelId AND state = 'ACTIVE'
            """, Map.of("parcelId", parcelId), (rs, row) -> new ActiveAssignment(
                rs.getObject("assignment_id", UUID.class), rs.getObject("courier_id", UUID.class),
                rs.getString("assigned_by"), rs.getTimestamp("assigned_at").toInstant()));
        return rows.stream().findFirst();
    }

    public Map<UUID, Long> activeAssignmentCounts() {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        jdbc.query("""
            SELECT courier_id, count(*) AS active_count
              FROM dispatch_assignments WHERE state = 'ACTIVE' GROUP BY courier_id
            """, Map.of(), rs -> {
                counts.put(rs.getObject("courier_id", UUID.class), rs.getLong("active_count"));
            });
        return counts;
    }

    public AssignmentResult saveAssignment(UUID parcelId, UUID courierId, UUID previousCourierId,
            String trackingNumber, String assignedBy, Instant assignedAt) {
        jdbc.update("""
            UPDATE dispatch_assignments
               SET state = 'CANCELLED', cancelled_by = :assignedBy, cancelled_at = :assignedAt
             WHERE parcel_id = :parcelId AND state = 'ACTIVE'
            """, new MapSqlParameterSource("assignedBy", assignedBy)
                .addValue("assignedAt", Timestamp.from(assignedAt)).addValue("parcelId", parcelId));
        jdbc.update("""
            UPDATE dispatch_parcels SET assigned_courier_id = :courierId, updated_at = :assignedAt
             WHERE parcel_id = :parcelId
            """, Map.of("courierId", courierId, "assignedAt", Timestamp.from(assignedAt), "parcelId", parcelId));
        UUID assignmentId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO dispatch_assignments
                (assignment_id, parcel_id, courier_id, previous_courier_id, tracking_number,
                 assigned_by, assigned_at, state)
            VALUES (:assignmentId, :parcelId, :courierId, :previousCourierId, :trackingNumber,
                 :assignedBy, :assignedAt, 'ACTIVE')
            """, new MapSqlParameterSource()
                .addValue("assignmentId", assignmentId)
                .addValue("parcelId", parcelId)
                .addValue("courierId", courierId)
                .addValue("previousCourierId", previousCourierId)
                .addValue("trackingNumber", trackingNumber)
                .addValue("assignedBy", assignedBy)
                .addValue("assignedAt", Timestamp.from(assignedAt)));
        return new AssignmentResult(assignmentId, parcelId, courierId, previousCourierId,
            assignedBy, assignedAt, "ACTIVE");
    }

    public Optional<AssignmentResult> cancelAssignment(UUID parcelId, String cancelledBy, Instant cancelledAt) {
        Optional<ActiveAssignment> active = activeAssignment(parcelId);
        if (active.isEmpty()) return Optional.empty();
        int changed = jdbc.update("""
            UPDATE dispatch_assignments
               SET state = 'CANCELLED', cancelled_by = :cancelledBy, cancelled_at = :cancelledAt
             WHERE assignment_id = :assignmentId AND state = 'ACTIVE'
            """, new MapSqlParameterSource("cancelledBy", cancelledBy)
                .addValue("cancelledAt", Timestamp.from(cancelledAt))
                .addValue("assignmentId", active.get().assignmentId()));
        if (changed == 0) return Optional.empty();
        jdbc.update("""
            UPDATE dispatch_parcels SET assigned_courier_id = NULL, updated_at = :cancelledAt
             WHERE parcel_id = :parcelId
            """, Map.of("cancelledAt", Timestamp.from(cancelledAt), "parcelId", parcelId));
        return Optional.of(new AssignmentResult(active.get().assignmentId(), parcelId,
            active.get().courierId(), null, active.get().assignedBy(),
            active.get().assignedAt(), "CANCELLED"));
    }

    public List<AssignedParcel> activeAssignedParcels(UUID courierId) {
        return jdbc.query("""
            SELECT a.parcel_id, a.tracking_number,
                   COALESCE(latest.new_status, p.status) AS parcel_status, a.assigned_at
              FROM dispatch_assignments a
              LEFT JOIN dispatch_parcels p ON p.parcel_id = a.parcel_id
              LEFT JOIN LATERAL (
                  SELECT new_status FROM dispatch_report_events e
                   WHERE e.parcel_id = a.parcel_id
                   ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1
              ) latest ON true
             WHERE a.courier_id = :courierId AND a.state = 'ACTIVE'
             ORDER BY a.assigned_at, a.assignment_id
            """, Map.of("courierId", courierId), (rs, row) -> new AssignedParcel(
                rs.getObject("parcel_id", UUID.class), rs.getString("tracking_number"),
                rs.getString("parcel_status"), rs.getTimestamp("assigned_at").toInstant()));
    }

    public List<CountByStatus> countStatuses(Instant from, Instant to) {
        return jdbc.query("""
            SELECT new_status AS status, count(DISTINCT parcel_id) AS total
              FROM dispatch_status_history
             WHERE changed_at >= :from AND changed_at < :to
             GROUP BY new_status ORDER BY new_status
            """, range(from, to), (rs, row) -> new CountByStatus(
                rs.getString("status"), rs.getLong("total")));
    }

    public List<VolumeByPeriod> volume(Instant from, Instant to, boolean weekly) {
        String bucket = weekly
            ? "date_trunc('week', changed_at AT TIME ZONE 'UTC')"
            : "date_trunc('day', changed_at AT TIME ZONE 'UTC')";
        String sql = """
            SELECT to_char(bucket, :format) AS period, count(DISTINCT parcel_id) AS total
              FROM (SELECT %s AS bucket, parcel_id FROM dispatch_status_history
                    WHERE changed_at >= :from AND changed_at < :to AND new_status = 'DELIVERED') events
             GROUP BY bucket ORDER BY bucket
            """.formatted(bucket);
        return jdbc.query(sql, new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                .addValue("format", weekly ? "IYYY-\"W\"IW" : "YYYY-MM-DD"),
            (rs, row) -> new VolumeByPeriod(rs.getString("period"), rs.getLong("total")));
    }

    public List<CourierPerformance> courierPerformance(Instant from, Instant to) {
        return jdbc.query("""
            WITH handled AS (
                SELECT courier_id, count(DISTINCT parcel_id) AS parcel_count
                  FROM dispatch_assignments
                 WHERE assigned_at >= :from AND assigned_at < :to
                 GROUP BY courier_id
            ), outcomes AS (
                SELECT p.assigned_courier_id AS courier_id,
                       count(DISTINCT p.parcel_id) FILTER (WHERE h.new_status = 'DELIVERED') AS delivered,
                       count(DISTINCT p.parcel_id) FILTER (WHERE h.new_status = 'FAILED') AS failed,
                       round(avg(EXTRACT(EPOCH FROM (h.changed_at - p.created_at)) / 3600.0)
                           FILTER (WHERE h.new_status = 'DELIVERED'), 2) AS average_hours
                  FROM dispatch_parcels p
                  JOIN dispatch_status_history h ON h.parcel_id = p.parcel_id
                       AND h.changed_at >= :from AND h.changed_at < :to
                       AND h.new_status IN ('DELIVERED', 'FAILED')
                 GROUP BY p.assigned_courier_id
            )
            SELECT c.courier_id, c.display_name, COALESCE(handled.parcel_count, 0) AS handled,
                   COALESCE(outcomes.delivered, 0) AS delivered,
                   COALESCE(outcomes.failed, 0) AS failed, outcomes.average_hours
              FROM dispatch_couriers c
              LEFT JOIN handled ON handled.courier_id = c.courier_id
              LEFT JOIN outcomes ON outcomes.courier_id = c.courier_id
             WHERE c.role = 'COURIER'
             ORDER BY c.display_name
            """, range(from, to), (rs, row) -> new CourierPerformance(
                rs.getObject("courier_id", UUID.class), rs.getString("display_name"),
                rs.getLong("handled"), rs.getLong("delivered"), rs.getLong("failed"),
                rs.getBigDecimal("average_hours")));
    }

    public List<FailedDelivery> failedDeliveries(Instant from, Instant to) {
        return jdbc.query("""
            SELECT p.tracking_number, c.display_name AS courier_name, p.pickup_area,
                   p.dropoff_area, COALESCE(p.failure_reason, h.note) AS reason,
                   h.changed_at AS failed_at
              FROM dispatch_parcels p
              LEFT JOIN dispatch_couriers c ON c.courier_id = p.assigned_courier_id
              LEFT JOIN LATERAL (
                  SELECT changed_at, note FROM dispatch_status_history
                   WHERE parcel_id = p.parcel_id AND new_status = 'FAILED'
                     AND changed_at >= :from AND changed_at < :to
                   ORDER BY changed_at DESC LIMIT 1
              ) h ON true
             WHERE p.status = 'FAILED' AND h.changed_at IS NOT NULL
             ORDER BY h.changed_at DESC
            """, range(from, to), (rs, row) -> new FailedDelivery(
                rs.getString("tracking_number"), rs.getString("courier_name"),
                rs.getString("pickup_area"), rs.getString("dropoff_area"),
                rs.getString("reason"), rs.getTimestamp("failed_at").toInstant()));
    }

    public List<RouteRevenue> revenueByRoute(Instant from, Instant to) {
        return jdbc.query("""
            SELECT pickup_area, dropoff_area, count(*) AS parcel_count, sum(charge) AS total_fees
              FROM dispatch_parcels
             WHERE created_at >= :from AND created_at < :to
             GROUP BY pickup_area, dropoff_area ORDER BY total_fees DESC, pickup_area, dropoff_area
            """, range(from, to), (rs, row) -> new RouteRevenue(
                rs.getString("pickup_area"), rs.getString("dropoff_area"),
                rs.getLong("parcel_count"), rs.getBigDecimal("total_fees")));
    }

    public boolean applyStatusEvent(ParcelStatusChangedEvent event) {
        UUID parcelId = UUID.fromString(event.parcelId());
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
        jdbc.update("""
            UPDATE dispatch_parcels
               SET status = :newStatus,
                   updated_at = :occurredAt,
                   delivered_at = CASE WHEN :newStatus = 'DELIVERED' THEN :occurredAt ELSE delivered_at END
             WHERE parcel_id = :parcelId AND updated_at <= :occurredAt
            """, new MapSqlParameterSource()
                .addValue("newStatus", event.newStatus())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                .addValue("parcelId", parcelId));
        jdbc.update("""
            INSERT INTO dispatch_status_history
                (history_id, parcel_id, previous_status, new_status, changed_at, changed_by)
            SELECT :id, parcel_id, :previousStatus, :newStatus, :changedAt, 'parcel-event'
              FROM dispatch_parcels WHERE parcel_id = :parcelId
            ON CONFLICT (history_id) DO NOTHING
            """, new MapSqlParameterSource()
                .addValue("id", event.eventId())
                .addValue("parcelId", parcelId)
                .addValue("previousStatus", event.oldStatus())
                .addValue("newStatus", event.newStatus())
                .addValue("changedAt", Timestamp.from(event.occurredAt())));
        if ("DELIVERED".equals(event.newStatus()) || "FAILED".equals(event.newStatus())) {
            jdbc.update("""
                UPDATE dispatch_assignments SET state = 'COMPLETED'
                 WHERE parcel_id = :parcelId AND state = 'ACTIVE'
                """, Map.of("parcelId", parcelId));
        }
        return true;
    }

    public List<DeliveryByDay> summaryDeliveriesPerDay(LocalDate from, LocalDate to) {
        return jdbc.query("""
            SELECT (days.day AT TIME ZONE 'UTC')::date AS report_date,
                   count(e.event_id) FILTER (WHERE e.new_status = 'DELIVERED') AS delivered,
                   count(e.event_id) FILTER (WHERE e.new_status = 'FAILED') AS failed
              FROM generate_series(
                  CAST(CAST(:from AS date) AS timestamp) AT TIME ZONE 'UTC',
                  CAST(CAST(:to AS date) - 1 AS timestamp) AT TIME ZONE 'UTC',
                  interval '1 day') days(day)
              LEFT JOIN dispatch_report_events e
                ON e.occurred_at >= days.day AND e.occurred_at < days.day + interval '1 day'
               AND e.new_status IN ('DELIVERED', 'FAILED')
             GROUP BY days.day ORDER BY days.day
            """, new MapSqlParameterSource("from", from).addValue("to", to),
            (rs, row) -> {
                long delivered = rs.getLong("delivered");
                long failed = rs.getLong("failed");
                return new DeliveryByDay(rs.getDate("report_date").toLocalDate(), delivered, failed, delivered + failed);
            });
    }

    public BigDecimal summarySuccessRate(Instant from, Instant to) {
        return jdbc.queryForObject("""
            SELECT CASE WHEN count(*) = 0 THEN 0
                        ELSE round(100.0 * count(*) FILTER (WHERE new_status = 'DELIVERED')
                            / count(*), 2) END
              FROM dispatch_report_events
             WHERE occurred_at >= :from AND occurred_at < :to
               AND new_status IN ('DELIVERED', 'FAILED')
            """, range(from, to), BigDecimal.class);
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

    public List<ActiveDeliveriesByCourier> summaryActiveByCourier(Instant asOf) {
        return jdbc.query("""
            WITH latest AS (
                SELECT DISTINCT ON (parcel_id) parcel_id, courier_id, new_status
                  FROM dispatch_report_events
                 WHERE occurred_at < :asOf
                 ORDER BY parcel_id, occurred_at DESC, event_id DESC
            )
            SELECT courier_id, count(*) AS total
              FROM latest
             WHERE courier_id IS NOT NULL
               AND new_status IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY')
             GROUP BY courier_id ORDER BY courier_id
            """, Map.of("asOf", Timestamp.from(asOf)), (rs, row) -> new ActiveDeliveriesByCourier(
                rs.getString("courier_id"), rs.getLong("total")));
    }

    public record ActiveAssignment(UUID assignmentId, UUID courierId, String assignedBy, Instant assignedAt) { }

    private static MapSqlParameterSource range(Instant from, Instant to) {
        return new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
    }

    private ParcelRow mapParcel(ResultSet rs, int row) throws SQLException {
        return new ParcelRow(
            rs.getObject("parcel_id", UUID.class),
            rs.getString("tracking_number"),
            rs.getString("pickup_area"),
            rs.getString("dropoff_area"),
            rs.getString("pickup_address"),
            rs.getString("delivery_address"),
            rs.getString("size"),
            rs.getString("status"),
            rs.getObject("assigned_courier_id", UUID.class),
            rs.getString("courier_name"),
            rs.getBigDecimal("charge"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }
}
