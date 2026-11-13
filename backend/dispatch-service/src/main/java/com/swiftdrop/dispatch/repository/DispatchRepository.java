package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CountByStatus;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierPerformance;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierWorkload;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    public List<CourierWorkload> couriers() {
        return jdbc.query("""
            SELECT c.courier_id, c.display_name, c.available, count(p.parcel_id) AS active_count
              FROM dispatch_couriers c
              LEFT JOIN dispatch_parcels p ON p.assigned_courier_id = c.courier_id
                   AND p.status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY')
             WHERE c.role = 'COURIER'
             GROUP BY c.courier_id, c.display_name, c.available
             ORDER BY c.display_name
            """, Map.of(), (rs, row) -> new CourierWorkload(
                rs.getObject("courier_id", UUID.class), rs.getString("display_name"),
                rs.getBoolean("available"), rs.getLong("active_count")));
    }

    public boolean isCourier(UUID id) {
        Integer matches = jdbc.queryForObject("""
            SELECT count(*) FROM dispatch_couriers WHERE courier_id = :id AND role = 'COURIER'
            """, Map.of("id", id), Integer.class);
        return matches != null && matches == 1;
    }

    public AssignmentResult saveAssignment(UUID parcelId, UUID courierId, UUID previousCourierId,
                                           String assignedBy, Instant assignedAt) {
        jdbc.update("""
            UPDATE dispatch_parcels SET assigned_courier_id = :courierId, updated_at = :assignedAt
             WHERE parcel_id = :parcelId
            """, Map.of("courierId", courierId, "assignedAt", Timestamp.from(assignedAt), "parcelId", parcelId));
        jdbc.update("""
            INSERT INTO dispatch_assignments
                (assignment_id, parcel_id, courier_id, previous_courier_id, assigned_by, assigned_at)
            VALUES (:assignmentId, :parcelId, :courierId, :previousCourierId, :assignedBy, :assignedAt)
            """, new MapSqlParameterSource()
                .addValue("assignmentId", UUID.randomUUID())
                .addValue("parcelId", parcelId)
                .addValue("courierId", courierId)
                .addValue("previousCourierId", previousCourierId)
                .addValue("assignedBy", assignedBy)
                .addValue("assignedAt", Timestamp.from(assignedAt)));
        return new AssignmentResult(parcelId, courierId, previousCourierId, assignedBy, assignedAt);
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
        return jdbc.query("""
            SELECT to_char(bucket, :format) AS period, count(DISTINCT parcel_id) AS total
              FROM (SELECT """ + bucket + """
 AS bucket, parcel_id FROM dispatch_status_history
                    WHERE changed_at >= :from AND changed_at < :to AND new_status = 'DELIVERED'
                    GROUP BY bucket, parcel_id) events
             GROUP BY bucket ORDER BY bucket
            """, new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
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

    public void applyStatusEvent(ParcelStatusChangedEvent event) {
        UUID parcelId = UUID.fromString(event.parcelId());
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
    }

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
