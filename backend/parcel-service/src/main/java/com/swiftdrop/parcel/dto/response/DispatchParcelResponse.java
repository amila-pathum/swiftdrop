package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.Parcel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DispatchParcelResponse(
    UUID parcelId,
    String trackingNumber,
    String pickupArea,
    String dropoffArea,
    String pickupAddress,
    String deliveryAddress,
    String size,
    String status,
    UUID courierId,
    Long courierUserId,
    String courierName,
    BigDecimal charge,
    Instant createdAt,
    Instant updatedAt
) {
    public static DispatchParcelResponse from(Parcel parcel) {
        return new DispatchParcelResponse(parcel.getId(), parcel.getTrackingNumber(),
            parcel.getPickupAddress(), parcel.getDeliveryAddress(), parcel.getPickupAddress(),
            parcel.getDeliveryAddress(), parcel.getSize(), parcel.getStatus().name(),
            parcel.getAssignedCourierId() == null ? parcel.getLegacyAssignedCourierUuid() : null,
            parcel.getAssignedCourierId(), null, parcel.getCharge(), parcel.getCreatedAt(),
            parcel.getUpdatedAt());
    }
}
