package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ActiveDeliveriesByCourier;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignedParcel;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CountByStatus;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierPerformance;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierProfile;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryByDay;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DispatchSummary;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.FailedDelivery;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.PageResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelDetail;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.RouteRevenue;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.StatusHistoryRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.VolumeByPeriod;
import com.swiftdrop.dispatch.client.DispatchDataClient;
import com.swiftdrop.dispatch.event.ParcelAssignedEvent;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.repository.DispatchRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;

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
    private final DispatchDataClient dataClient;

    public DispatchService(DispatchRepository repository, ApplicationEventPublisher events,
            DispatchDataClient dataClient) {
        this.repository = repository;
        this.events = events;
        this.dataClient = dataClient;
    }

    public PageResult<ParcelRow> unassigned(String pickupArea, String dropoffArea,
            LocalDate from, LocalDate to, String size, int page, int pageSize,
            String sort, String direction) {
        validatePaging(page, pageSize);
        validateDateRange(from, to);
        if (hasText(size) && size.trim().length() > 30) throw badRequest("Size filter is too long");
        List<ParcelRow> filtered = dataClient.createdParcels().stream()
            .filter(parcel -> "CREATED".equals(parcel.status()))
            .filter(parcel -> !repository.hasActiveAssignment(parcel.parcelId()))
            .filter(parcel -> matches(parcel.pickupArea(), pickupArea))
            .filter(parcel -> matches(parcel.dropoffArea(), dropoffArea))
            .filter(parcel -> matches(parcel.size(), size))
            .filter(parcel -> from == null || !parcel.createdAt().isBefore(start(from)))
            .filter(parcel -> to == null || parcel.createdAt().isBefore(end(to)))
            .sorted(parcelComparator(sort, direction))
            .toList();
        return page(filtered, page, pageSize);
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
        Map<UUID, Long> counts = repository.activeAssignmentCounts();
        return dataClient.couriers().stream()
            .map(courier -> new CourierWorkload(courier.courierId(), courier.name(),
                courier.active(), courier.available(), counts.getOrDefault(courier.courierId(), 0L)))
            .toList();
    }

    public PageResult<ParcelRow> deliveries(String status, String trackingNumber,
            int page, int pageSize, String sort, String direction) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE p.status IN "
            + "('PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED')");
        if (hasText(status)) {
            String normalized = status.trim().toUpperCase();
            if (!Set.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED").contains(normalized)) {
                throw badRequest("Invalid delivery status");
            }
            where.append(" AND p.status = :status");
            params.addValue("status", normalized);
        }
        if (hasText(trackingNumber)) {
            String escaped = trackingNumber.trim().replace("!", "!!")
                .replace("%", "!%").replace("_", "!_");
            if (escaped.length() > 80) throw badRequest("Tracking search is too long");
            where.append(" AND p.tracking_number ILIKE :tracking ESCAPE '!'");
            params.addValue("tracking", "%" + escaped + "%");
        }
        return page(where.toString(), params, page, pageSize, sort, direction);
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
        ParcelRow parcel = dataClient.findParcel(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found"));
        parcel = dataClient.lockParcel(parcelId).orElse(parcel);
        if (!"CREATED".equals(parcel.status())) {
            throw new DispatchException(HttpStatus.CONFLICT,
                "Courier can only be assigned or changed before pickup");
        }
        Optional<DispatchRepository.ActiveAssignment> activeAssignment = repository.activeAssignment(parcelId);
        if (activeAssignment.isPresent() && activeAssignment.get().courierId().equals(courierId)) {
            throw new DispatchException(HttpStatus.CONFLICT, "Parcel is already assigned to this courier");
        }
        CourierProfile courier = dataClient.findCourier(courierId).orElse(null);
        if (courier == null) {
            throw new DispatchException(HttpStatus.BAD_REQUEST, "Courier not found");
        }
        if (!courier.active() || !courier.available()) {
            throw new DispatchException(HttpStatus.CONFLICT,
                "Courier must be active and available to receive an assignment");
        }
        UUID previous = activeAssignment.map(DispatchRepository.ActiveAssignment::courierId).orElse(null);
        Instant assignedAt = Instant.now();
        AssignmentResult result;
        try {
            result = repository.saveAssignment(
                parcelId, courierId, previous, parcel.trackingNumber(), assignedBy, assignedAt);
        } catch (DataIntegrityViolationException exception) {
            throw new DispatchException(HttpStatus.CONFLICT,
                "Parcel already has an active assignment", exception);
        }
        events.publishEvent(new ParcelAssignedEvent(
            UUID.randomUUID(), "ParcelAssigned", assignedAt, parcelId.toString(),
            parcel.trackingNumber(), courierId.toString(),
            previous == null ? null : previous.toString(), assignedBy));
        return result;
    }

    @Transactional
    public AssignmentResult cancelAssignment(UUID parcelId, String cancelledBy) {
        if (cancelledBy == null || cancelledBy.isBlank() || cancelledBy.length() > 80) {
            throw new DispatchException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
        ParcelRow parcel = dataClient.findParcel(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found"));
        parcel = dataClient.lockParcel(parcelId).orElse(parcel);
        if (!"CREATED".equals(parcel.status())) {
            throw new DispatchException(HttpStatus.CONFLICT,
                "An assignment can only be cancelled before pickup");
        }
        return repository.cancelAssignment(parcelId, cancelledBy, Instant.now())
            .orElseThrow(() -> new DispatchException(HttpStatus.CONFLICT, "Parcel has no active assignment"));
    }

    public List<AssignedParcel> assignedParcels(UUID courierId) {
        return repository.activeAssignedParcels(courierId);
    }

    @Transactional
    public void applyStatusEvent(ParcelStatusChangedEvent event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
                || !"ParcelStatusChanged".equals(event.eventType())
                || !hasText(event.parcelId()) || !hasText(event.trackingNumber())
                || !hasText(event.customerId()) || !hasText(event.oldStatus())
                || !hasText(event.newStatus())
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

    public DispatchSummary reportSummary(LocalDate from, LocalDate to) {
        Instant start = start(from);
        Instant end = end(to);
        return new DispatchSummary(repository.summaryDeliveriesPerDay(from, to.plusDays(1)),
            repository.summarySuccessRate(start, end),
            repository.summaryCountsByStatus(start, end),
            repository.summaryActiveByCourier(end));
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

    private PageResult<ParcelRow> page(List<ParcelRow> all, int page, int pageSize) {
        int start = (int) Math.min(all.size(), (long) page * pageSize);
        int end = (int) Math.min(all.size(), (long) start + pageSize);
        return new PageResult<>(all.subList(start, end), page, pageSize, all.size(),
            (int) Math.ceil((double) all.size() / pageSize));
    }

    private static boolean matches(String actual, String filter) {
        return !hasText(filter) || actual != null
            && actual.toLowerCase().contains(filter.trim().toLowerCase());
    }

    private static Comparator<ParcelRow> parcelComparator(String sort, String direction) {
        Comparator<ParcelRow> comparator = switch (sort) {
            case "createdAt" -> Comparator.comparing(ParcelRow::createdAt);
            case "updatedAt" -> Comparator.comparing(ParcelRow::updatedAt);
            case "trackingNumber" -> Comparator.comparing(ParcelRow::trackingNumber);
            case "status" -> Comparator.comparing(ParcelRow::status);
            case "pickupArea" -> Comparator.comparing(ParcelRow::pickupArea);
            case "dropoffArea" -> Comparator.comparing(ParcelRow::dropoffArea);
            case "size" -> Comparator.comparing(ParcelRow::size);
            default -> throw badRequest("Unsupported sort field");
        };
        if (direction.equalsIgnoreCase("desc")) comparator = comparator.reversed();
        else if (!direction.equalsIgnoreCase("asc")) throw badRequest("Sort direction must be asc or desc");
        return comparator.thenComparing(ParcelRow::parcelId);
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("Start date must not be after end date");
        }
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
