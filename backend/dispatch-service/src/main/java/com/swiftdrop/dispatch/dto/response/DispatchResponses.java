package com.swiftdrop.dispatch.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DispatchResponses {
    private DispatchResponses() { }

    /**
     * A parcel as parcel-service returns it from GET /api/parcels/dispatch.
     * {@code courierUserId} is the identity user id parcel-service holds as the
     * assigned courier.
     */
    public record ParcelRow(
        UUID parcelId,
        String trackingNumber,
        String pickupArea,
        String dropoffArea,
        String pickupAddress,
        String deliveryAddress,
        String size,
        String status,
        Long courierUserId,
        BigDecimal charge,
        Instant createdAt,
        Instant updatedAt
    ) { }

    /** One stored parcel status event. */
    public record StatusHistoryRow(String previousStatus, String newStatus, Instant changedAt) { }

    public record ParcelDetail(
        ParcelRow parcel,
        List<StatusHistoryRow> history,
        List<AssignmentRow> assignments
    ) { }

    /**
     * A courier with its workload. {@code courierId} is the courier-service
     * profile id; {@code available} is true only for an active courier whose
     * availability is AVAILABLE.
     */
    public record CourierWorkload(
        Long courierId,
        Long userId,
        String name,
        boolean active,
        String availability,
        boolean available,
        long activeParcelCount
    ) { }

    public record AssignmentResult(
        UUID assignmentId,
        UUID parcelId,
        String trackingNumber,
        Long courierId,
        String courierName,
        Long previousCourierId,
        String assignedBy,
        Instant assignedAt,
        String state
    ) { }

    /** An assignment with the parcel's latest known status (CREATED until an event says otherwise). */
    public record AssignmentRow(
        UUID assignmentId,
        UUID parcelId,
        String trackingNumber,
        Long courierId,
        String courierName,
        String assignedBy,
        Instant assignedAt,
        String state,
        String parcelStatus
    ) { }

    /** The assignment shape courier-service reads (CourierIntegrations.Assignment). */
    public record CourierAssignment(
        UUID id,
        UUID parcelId,
        Long courierId,
        int attemptNo,
        boolean active,
        long version,
        LocalDate deliveryDate,
        Instant assignedAt
    ) { }

    /** A delivery as seen through its latest stored status event. */
    public record DeliveryRow(
        UUID parcelId,
        String trackingNumber,
        String status,
        Long courierId,
        String courierName,
        Instant lastUpdatedAt
    ) { }

    public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) { }

    public record CountByStatus(String status, long count) { }
    public record DeliveryByDay(LocalDate date, long delivered, long failed, long total) { }

    /**
     * {@code courierId} is the courier-service profile id when dispatch has an
     * assignment for that courier; {@code courierUserId} is the id carried by
     * the parcel status events.
     */
    public record ActiveDeliveriesByCourier(Long courierId, String courierUserId, String courierName, long count) { }

    public record DispatchSummary(
        List<DeliveryByDay> deliveriesPerDay,
        BigDecimal successRate,
        BigDecimal failureRate,
        List<CountByStatus> countsByStatus,
        List<ActiveDeliveriesByCourier> activeDeliveriesPerCourier
    ) { }
}
