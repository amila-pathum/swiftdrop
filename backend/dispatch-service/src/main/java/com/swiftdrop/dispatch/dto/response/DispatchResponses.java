package com.swiftdrop.dispatch.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DispatchResponses {
    private DispatchResponses() { }

    public record ParcelRow(
        UUID parcelId,
        String trackingNumber,
        String pickupArea,
        String dropoffArea,
        String pickupAddress,
        String deliveryAddress,
        String size,
        String status,
        UUID courierId,
        String courierName,
        BigDecimal charge,
        Instant createdAt,
        Instant updatedAt
    ) { }

    public record StatusHistoryRow(
        String previousStatus,
        String newStatus,
        Instant changedAt,
        String changedBy,
        String note
    ) { }

    public record ParcelDetail(ParcelRow parcel, List<StatusHistoryRow> history) { }

    public record CourierWorkload(
        UUID courierId,
        String name,
        boolean active,
        boolean available,
        long activeParcelCount
    ) { }

    public record CourierProfile(UUID courierId, String name, boolean active, boolean available) { }

    public record AssignmentResult(
        UUID assignmentId,
        UUID parcelId,
        UUID courierId,
        UUID previousCourierId,
        String assignedBy,
        Instant assignedAt,
        String state
    ) { }

    public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) { }

    public record CountByStatus(String status, long count) { }
    public record VolumeByPeriod(String period, long count) { }
    public record CourierPerformance(
        UUID courierId,
        String courierName,
        long parcelsHandled,
        long delivered,
        long failed,
        BigDecimal averageDeliveryHours
    ) { }
    public record FailedDelivery(
        String trackingNumber,
        String courierName,
        String pickupArea,
        String dropoffArea,
        String reason,
        Instant failedAt
    ) { }
    public record RouteRevenue(
        String pickupArea,
        String dropoffArea,
        long parcelCount,
        BigDecimal totalFees
    ) { }
    public record DeliveryByDay(LocalDate date, long delivered, long failed, long total) { }
    public record ActiveDeliveriesByCourier(String courierId, long count) { }
    public record DispatchSummary(
        List<DeliveryByDay> deliveriesPerDay,
        BigDecimal successRate,
        List<CountByStatus> countsByStatus,
        List<ActiveDeliveriesByCourier> activeDeliveriesPerCourier
    ) { }
    public record AssignedParcel(UUID parcelId, String trackingNumber, String status, Instant assignedAt) { }
}
