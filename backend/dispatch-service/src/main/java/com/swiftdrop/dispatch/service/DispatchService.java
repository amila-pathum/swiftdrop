package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CountByStatus;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierPerformance;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.FailedDelivery;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.PageResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelDetail;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.RouteRevenue;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.StatusHistoryRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.VolumeByPeriod;
import com.swiftdrop.dispatch.event.ParcelAssignedEvent;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.repository.DispatchRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.sql.Timestamp;

@Service
public class DispatchService {
    private static final List<String> REPORT_STATUSES =
        List.of("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED");
    private static final Set<String> VALID_EVENT_STATUSES =
        Set.of("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED");
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "createdAt", "p.created_at",
        "updatedAt", "p.updated_at",
        "trackingNumber", "p.tracking_number",
        "status", "p.status",
        "pickupArea", "p.pickup_area",
        "dropoffArea", "p.dropoff_area",
        "size", "p.size");

    private final DispatchRepository repository;
    private final ApplicationEventPublisher events;

    public DispatchService(DispatchRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResult<ParcelRow> unassigned(String pickupArea, String dropoffArea,
            LocalDate from, LocalDate to, String size, int page, int pageSize,
            String sort, String direction) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE p.status = 'CREATED' AND p.assigned_courier_id IS NULL");
        addTextFilter(where, params, "p.pickup_area", "pickupArea", pickupArea);
        addTextFilter(where, params, "p.dropoff_area", "dropoffArea", dropoffArea);
        if (hasText(size) && size.trim().length() > 30) throw badRequest("Size filter is too long");
        addTextFilter(where, params, "p.size", "size", size);
        addDateFilter(where, params, from, to);
        return page(where.toString(), params, page, pageSize, sort, direction);
    }

    public PageResult<ParcelRow> parcels(String status, UUID courierId, LocalDate from,
            LocalDate to, String trackingNumber, int page, int pageSize,
            String sort, String direction) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (hasText(status)) {
            String normalized = status.trim().toUpperCase();
            if (!Set.of("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY",
                    "DELIVERED", "FAILED", "CANCELLED").contains(normalized)) {
                throw badRequest("Invalid parcel status");
            }
            where.append(" AND p.status = :status");
            params.addValue("status", normalized);
        }
        if (courierId != null) {
            where.append(" AND p.assigned_courier_id = :courierId");
            params.addValue("courierId", courierId);
        }
        if (hasText(trackingNumber)) {
            String escaped = trackingNumber.trim().replace("!", "!!")
                .replace("%", "!%").replace("_", "!_");
            if (escaped.length() > 80) throw badRequest("Tracking search is too long");
            where.append(" AND p.tracking_number ILIKE :tracking ESCAPE '!'");
            params.addValue("tracking", "%" + escaped + "%");
        }
        addDateFilter(where, params, from, to);
        return page(where.toString(), params, page, pageSize, sort, direction);
    }

    public List<CourierWorkload> couriers() {
        return repository.couriers();
    }

    public ParcelDetail detail(UUID parcelId) {
        ParcelRow parcel = repository.findParcel(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found"));
        return new ParcelDetail(parcel, repository.history(parcelId));
    }

    @Transactional
    public AssignmentResult assign(UUID parcelId, UUID courierId, String assignedBy) {
        if (courierId == null) throw badRequest("Courier is required");
        if (assignedBy == null || assignedBy.isBlank() || assignedBy.length() > 80) {
            throw new DispatchException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
        ParcelRow parcel = repository.lockParcel(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!repository.isCourier(courierId)) {
            throw new DispatchException(HttpStatus.BAD_REQUEST, "Courier not found");
        }
        if (!"CREATED".equals(parcel.status())) {
            throw new DispatchException(HttpStatus.CONFLICT,
                "Courier can only be assigned or changed before pickup");
        }
        UUID previous = parcel.courierId();
        if (courierId.equals(previous)) {
            throw new DispatchException(HttpStatus.CONFLICT, "Parcel is already assigned to this courier");
        }
        Instant assignedAt = Instant.now();
        AssignmentResult result = repository.saveAssignment(
            parcelId, courierId, previous, assignedBy, assignedAt);
        events.publishEvent(new ParcelAssignedEvent(
            UUID.randomUUID(), "ParcelAssigned", assignedAt, parcelId.toString(),
            parcel.trackingNumber(), courierId.toString(),
            previous == null ? null : previous.toString(), assignedBy));
        return result;
    }

    public void applyStatusEvent(ParcelStatusChangedEvent event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
                || !"ParcelStatusChanged".equals(event.eventType())
                || !hasText(event.parcelId()) || !hasText(event.newStatus())
                || !VALID_EVENT_STATUSES.contains(event.newStatus())) {
            throw badRequest("Invalid parcel status event");
        }
        try {
            UUID.fromString(event.parcelId());
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid parcel status event");
        }
        repository.applyStatusEvent(event);
    }

    public List<CountByStatus> reportStatuses(LocalDate from, LocalDate to) {
        var counts = new LinkedHashMap<String, Long>();
        REPORT_STATUSES.forEach(status -> counts.put(status, 0L));
        repository.countStatuses(start(from), end(to)).forEach(row -> {
            if (counts.containsKey(row.status())) counts.put(row.status(), row.count());
        });
        return counts.entrySet().stream().map(entry -> new CountByStatus(entry.getKey(), entry.getValue())).toList();
    }

    public List<VolumeByPeriod> reportVolume(LocalDate from, LocalDate to, boolean weekly) {
        return repository.volume(start(from), end(to), weekly);
    }

    public List<CourierPerformance> reportCourierPerformance(LocalDate from, LocalDate to) {
        return repository.courierPerformance(start(from), end(to));
    }

    public List<FailedDelivery> reportFailed(LocalDate from, LocalDate to) {
        return repository.failedDeliveries(start(from), end(to));
    }

    public List<RouteRevenue> reportRevenue(LocalDate from, LocalDate to) {
        return repository.revenueByRoute(start(from), end(to));
    }

    static Instant start(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static Instant end(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private PageResult<ParcelRow> page(String where, MapSqlParameterSource params, int page,
            int pageSize, String sort, String direction) {
        validatePaging(page, pageSize);
        String column = SORT_FIELDS.get(sort);
        if (column == null) throw badRequest("Unsupported sort field");
        String order = direction.equalsIgnoreCase("asc") ? "ASC"
            : direction.equalsIgnoreCase("desc") ? "DESC" : null;
        if (order == null) throw badRequest("Sort direction must be asc or desc");
        long total = repository.countParcels(where, params);
        List<ParcelRow> content = repository.findParcels(where, params,
            column + " " + order + ", p.parcel_id", pageSize, (long) page * pageSize);
        int pages = (int) Math.ceil((double) total / pageSize);
        return new PageResult<>(content, page, pageSize, total, pages);
    }

    private static void validatePaging(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw badRequest("Page must be non-negative and page size must be between 1 and 100");
        }
    }

    private static void addTextFilter(StringBuilder where, MapSqlParameterSource params,
            String column, String name, String value) {
        if (!hasText(value)) return;
        String clean = value.trim();
        if (clean.length() > 120) throw badRequest("Filter value is too long");
        where.append(" AND ").append(column).append(" ILIKE :").append(name);
        params.addValue(name, "%" + clean.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        where.append(" ESCAPE '!'");
    }

    private static void addDateFilter(StringBuilder where, MapSqlParameterSource params,
            LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("Start date must not be after end date");
        }
        if (from != null) {
            where.append(" AND p.created_at >= :from");
            params.addValue("from", Timestamp.from(start(from)));
        }
        if (to != null) {
            where.append(" AND p.created_at < :to");
            params.addValue("to", Timestamp.from(end(to)));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DispatchException badRequest(String message) {
        return new DispatchException(HttpStatus.BAD_REQUEST, message);
    }
}
