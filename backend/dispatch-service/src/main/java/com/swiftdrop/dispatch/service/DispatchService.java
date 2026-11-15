package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.client.DispatchDataClient;
import com.swiftdrop.dispatch.client.DispatchDataClient.CourierData;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierAssignment;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryByDay;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DispatchSummary;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.PageResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelDetail;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.event.ParcelAssignedEvent;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.repository.DispatchRepository;
import com.swiftdrop.dispatch.repository.DispatchRepository.ActiveAssignment;
import com.swiftdrop.dispatch.repository.DispatchRepository.CourierAssignmentRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DispatchService {
    private static final Set<String> VALID_EVENT_STATUSES =
        Set.of("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED");
    private static final List<String> DELIVERY_STATUSES =
        List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED");
    private static final Set<String> ASSIGNMENT_STATES = Set.of("ACTIVE", "COMPLETED", "CANCELLED");

    private final DispatchRepository repository;
    private final ApplicationEventPublisher events;
    private final DispatchDataClient dataClient;

    public DispatchService(DispatchRepository repository, ApplicationEventPublisher events,
            DispatchDataClient dataClient) {
        this.repository = repository;
        this.events = events;
        this.dataClient = dataClient;
    }

    // ---------------------------------------------------------------- reads

    /** CREATED parcels from parcel-service that have no ACTIVE assignment here. */
    public PageResult<ParcelRow> unassigned(String pickupArea, String dropoffArea,
            LocalDate from, LocalDate to, String size, int page, int pageSize,
            String sort, String direction) {
        validatePaging(page, pageSize);
        validateDateRange(from, to);
        if (hasText(size) && size.trim().length() > 30) throw badRequest("Size filter is too long");
        Comparator<ParcelRow> order = parcelComparator(sort, direction);
        Set<UUID> assigned = repository.activeAssignedParcelIds();
        List<ParcelRow> filtered = dataClient.createdParcels().stream()
            .filter(parcel -> parcel.parcelId() != null && "CREATED".equals(parcel.status()))
            .filter(parcel -> !assigned.contains(parcel.parcelId()))
            .filter(parcel -> matches(parcel.pickupArea(), pickupArea))
            .filter(parcel -> matches(parcel.dropoffArea(), dropoffArea))
            .filter(parcel -> matches(parcel.size(), size))
            .filter(parcel -> from == null || parcel.createdAt() != null && !parcel.createdAt().isBefore(start(from)))
            .filter(parcel -> to == null || parcel.createdAt() != null && parcel.createdAt().isBefore(end(to)))
            .sorted(order)
            .toList();
        return page(filtered, page, pageSize);
    }

    /** Every active courier from courier-service with its number of ACTIVE assignments. */
    public List<CourierWorkload> couriers() {
        Map<Long, Long> counts = repository.activeAssignmentCounts();
        return dataClient.couriers().stream()
            .filter(courier -> courier.id() != null)
            .map(courier -> new CourierWorkload(courier.id(), courier.userId(), courier.name(),
                courier.active(), courier.availability(), courier.available(),
                counts.getOrDefault(courier.id(), 0L)))
            .sorted(Comparator.comparing(CourierWorkload::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    public PageResult<AssignmentRow> assignments(String state, Long courierId, String trackingNumber,
            int page, int pageSize) {
        validatePaging(page, pageSize);
        String normalizedState = null;
        if (hasText(state)) {
            normalizedState = state.trim().toUpperCase();
            if (!ASSIGNMENT_STATES.contains(normalizedState)) throw badRequest("Invalid assignment state");
        }
        String pattern = trackingPattern(trackingNumber);
        long total = repository.countAssignments(normalizedState, courierId, pattern);
        List<AssignmentRow> rows = repository.findAssignments(normalizedState, courierId, pattern,
            pageSize, (long) page * pageSize);
        return new PageResult<>(rows, page, pageSize, total, totalPages(total, pageSize));
    }

    /**
     * Deliveries by their latest stored status event. Without a status filter
     * only parcels out with a courier (PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY) are returned.
     */
    public PageResult<DeliveryRow> deliveries(String status, String trackingNumber, int page, int pageSize) {
        validatePaging(page, pageSize);
        List<String> statuses = DispatchRepository.IN_PROGRESS_STATUSES;
        if (hasText(status)) {
            String normalized = status.trim().toUpperCase();
            if (!DELIVERY_STATUSES.contains(normalized)) throw badRequest("Invalid delivery status");
            statuses = List.of(normalized);
        }
        String pattern = trackingPattern(trackingNumber);
        long total = repository.countDeliveries(statuses, pattern);
        List<DeliveryRow> rows = repository.findDeliveries(statuses, pattern, pageSize, (long) page * pageSize);
        return new PageResult<>(rows, page, pageSize, total, totalPages(total, pageSize));
    }

    public ParcelDetail detail(UUID parcelId) {
        ParcelRow parcel = dataClient.findParcel(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found"));
        return new ParcelDetail(parcel, repository.history(parcelId), repository.parcelAssignments(parcelId));
    }

    /**
     * A courier's assignments in the shape courier-service reads. A COURIER may
     * only read their own: every row must belong to their identity user id.
     */
    public List<CourierAssignment> courierAssignments(Long courierProfileId, Long callerUserId, boolean callerIsCourier) {
        List<CourierAssignmentRecord> records = repository.courierAssignments(courierProfileId);
        if (callerIsCourier && records.stream().anyMatch(record -> !record.courierUserId().equals(callerUserId))) {
            throw new DispatchException(HttpStatus.FORBIDDEN, "Access is denied");
        }
        return records.stream().map(record -> new CourierAssignment(record.assignmentId(), record.parcelId(),
                record.courierProfileId(), record.attemptNo(), "ACTIVE".equals(record.state()),
                record.version(), record.deliveryDate(), record.assignedAt()))
            .toList();
    }

    // ---------------------------------------------------------------- assignment changes

    /**
     * Assigns a courier to a parcel that is CREATED and has no ACTIVE assignment.
     * The local row and the parcel-service update succeed or fail together: if
     * parcel-service refuses, the exception rolls the insert back.
     */
    @Transactional
    public AssignmentResult assign(UUID parcelId, Long courierId, String assignedBy) {
        requireActor(assignedBy);
        if (parcelId == null) throw badRequest("Parcel is required");
        if (courierId == null) throw badRequest("Courier is required");
        ParcelRow parcel = waitingParcel(parcelId, "Parcel is not waiting for a courier");
        if (repository.activeAssignment(parcelId).isPresent()) {
            throw new DispatchException(HttpStatus.CONFLICT, "Parcel already has an active assignment");
        }
        CourierData courier = assignableCourier(courierId);
        Instant now = Instant.now();
        AssignmentResult result = insert(parcel, courier, null, assignedBy, now);
        dataClient.assignParcelCourier(parcelId, courier.userId());
        publish(result, parcel.trackingNumber(), courier, null, assignedBy, now);
        return result;
    }

    /** Moves an ACTIVE assignment to another courier before pickup. */
    @Transactional
    public AssignmentResult reassign(UUID parcelId, Long courierId, String reassignedBy) {
        requireActor(reassignedBy);
        if (courierId == null) throw badRequest("Courier is required");
        ParcelRow parcel = waitingParcel(parcelId, "Courier can only be changed before pickup");
        ActiveAssignment current = repository.activeAssignment(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.CONFLICT, "Parcel has no active assignment"));
        if (current.courierProfileId().equals(courierId)) {
            throw new DispatchException(HttpStatus.CONFLICT, "Parcel is already assigned to this courier");
        }
        CourierData courier = assignableCourier(courierId);
        Instant now = Instant.now();
        repository.cancelActiveAssignment(parcelId, reassignedBy, now);
        AssignmentResult result = insert(parcel, courier, current.courierProfileId(), reassignedBy, now);
        dataClient.assignParcelCourier(parcelId, courier.userId());
        publish(result, parcel.trackingNumber(), courier, current, reassignedBy, now);
        return result;
    }

    /** Cancels an ACTIVE assignment before pickup; the parcel returns to the waiting list. */
    @Transactional
    public AssignmentResult cancelAssignment(UUID parcelId, String cancelledBy) {
        requireActor(cancelledBy);
        waitingParcel(parcelId, "An assignment can only be cancelled before pickup");
        ActiveAssignment cancelled = repository.cancelActiveAssignment(parcelId, cancelledBy, Instant.now())
            .orElseThrow(() -> new DispatchException(HttpStatus.CONFLICT, "Parcel has no active assignment"));
        // TODO(R1): clear the courier in parcel-service once it offers an un-assign call.
        // Until then parcel-service keeps the previous courierUserId; the next assignment overwrites it.
        return new AssignmentResult(cancelled.assignmentId(), parcelId, cancelled.trackingNumber(),
            cancelled.courierProfileId(), cancelled.courierName(), null, cancelled.assignedBy(),
            cancelled.assignedAt(), "CANCELLED");
    }

    // ---------------------------------------------------------------- events

    /**
     * Validates and stores one parcel status event. Throws a 400
     * {@link DispatchException} for an event that can never be stored.
     */
    @Transactional
    public boolean applyStatusEvent(ParcelStatusChangedEvent event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
                || !"ParcelStatusChanged".equals(event.eventType())
                || !hasText(event.parcelId()) || !hasText(event.trackingNumber())
                || !hasText(event.customerId())
                || !hasText(event.newStatus())
                || !VALID_EVENT_STATUSES.contains(event.newStatus())
                || ("CREATED".equals(event.newStatus())
                    ? event.oldStatus() != null
                    : !hasText(event.oldStatus()))) {
            throw badRequest("Invalid parcel status event");
        }
        try {
            UUID.fromString(event.parcelId());
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid parcel status event");
        }
        return repository.applyStatusEvent(event);
    }

    // ---------------------------------------------------------------- reports

    public DispatchSummary reportSummary(LocalDate from, LocalDate to) {
        List<DeliveryByDay> perDay = repository.summaryDeliveriesPerDay(from, to);
        long delivered = perDay.stream().mapToLong(DeliveryByDay::delivered).sum();
        long failed = perDay.stream().mapToLong(DeliveryByDay::failed).sum();
        BigDecimal successRate = percentage(delivered, delivered + failed);
        BigDecimal failureRate = delivered + failed == 0 ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(100).setScale(2).subtract(successRate);
        return new DispatchSummary(perDay, successRate, failureRate,
            repository.summaryCountsByStatus(start(from), end(to)),
            repository.summaryActiveByCourier(end(to)));
    }

    // ---------------------------------------------------------------- helpers

    private ParcelRow waitingParcel(UUID parcelId, String notWaitingMessage) {
        ParcelRow parcel = dataClient.findParcel(parcelId)
            .orElseThrow(() -> new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!"CREATED".equals(parcel.status())) {
            throw new DispatchException(HttpStatus.CONFLICT, notWaitingMessage);
        }
        return parcel;
    }

    private CourierData assignableCourier(Long courierId) {
        CourierData courier = dataClient.findCourier(courierId)
            .orElseThrow(() -> badRequest("Courier not found"));
        if (!courier.available()) {
            throw new DispatchException(HttpStatus.CONFLICT, "Courier must be active and AVAILABLE");
        }
        if (courier.userId() == null) {
            throw new DispatchException(HttpStatus.BAD_GATEWAY, "Courier service returned a courier without a user id");
        }
        return courier;
    }

    private AssignmentResult insert(ParcelRow parcel, CourierData courier, Long previousCourierId,
            String assignedBy, Instant now) {
        try {
            return repository.insertAssignment(parcel.parcelId(), parcel.trackingNumber(), courier.id(),
                courier.userId(), courierName(courier), previousCourierId, assignedBy, now);
        } catch (DataIntegrityViolationException exception) {
            throw new DispatchException(HttpStatus.CONFLICT, "Parcel already has an active assignment", exception);
        }
    }

    private void publish(AssignmentResult result, String trackingNumber, CourierData courier,
            ActiveAssignment previous, String assignedBy, Instant now) {
        events.publishEvent(new ParcelAssignedEvent(UUID.randomUUID(), "ParcelAssigned", now,
            result.parcelId().toString(), trackingNumber,
            courier.id().toString(), courier.userId().toString(),
            previous == null ? null : previous.courierProfileId().toString(),
            previous == null ? null : previous.courierUserId().toString(),
            assignedBy));
    }

    private static String courierName(CourierData courier) {
        String name = hasText(courier.name()) ? courier.name().trim() : "Courier " + courier.id();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 80) {
            throw new DispatchException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
    }

    static BigDecimal percentage(long part, long total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    static Instant start(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static Instant end(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static String trackingPattern(String trackingNumber) {
        if (!hasText(trackingNumber)) return null;
        String escaped = trackingNumber.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        if (escaped.length() > 80) throw badRequest("Tracking search is too long");
        return "%" + escaped + "%";
    }

    private static int totalPages(long total, int pageSize) {
        return (int) Math.ceil((double) total / pageSize);
    }

    private static PageResult<ParcelRow> page(List<ParcelRow> all, int page, int pageSize) {
        int start = (int) Math.min(all.size(), (long) page * pageSize);
        int end = (int) Math.min(all.size(), (long) start + pageSize);
        return new PageResult<>(all.subList(start, end), page, pageSize, all.size(), totalPages(all.size(), pageSize));
    }

    private static boolean matches(String actual, String filter) {
        return !hasText(filter) || actual != null
            && actual.toLowerCase().contains(filter.trim().toLowerCase());
    }

    private static Comparator<ParcelRow> parcelComparator(String sort, String direction) {
        Comparator<ParcelRow> comparator = switch (sort) {
            case "createdAt" -> Comparator.comparing(ParcelRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "updatedAt" -> Comparator.comparing(ParcelRow::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "trackingNumber" -> Comparator.comparing(ParcelRow::trackingNumber, Comparator.nullsLast(Comparator.naturalOrder()));
            case "pickupArea" -> Comparator.comparing(ParcelRow::pickupArea, Comparator.nullsLast(Comparator.naturalOrder()));
            case "dropoffArea" -> Comparator.comparing(ParcelRow::dropoffArea, Comparator.nullsLast(Comparator.naturalOrder()));
            case "size" -> Comparator.comparing(ParcelRow::size, Comparator.nullsLast(Comparator.naturalOrder()));
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DispatchException badRequest(String message) {
        return new DispatchException(HttpStatus.BAD_REQUEST, message);
    }
}
