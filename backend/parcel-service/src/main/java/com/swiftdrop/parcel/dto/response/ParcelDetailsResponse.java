package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contains contact details and addresses; only for authenticated, authorized users. */
public record ParcelDetailsResponse(
    UUID id,
    String trackingNumber,
    Long senderId,
    String receiverName,
    String receiverPhone,
    String pickupAddress,
    String deliveryAddress,
    BigDecimal weight,
    String size,
    String parcelType,
    BigDecimal charge,
    ParcelStatus status,
    Long merchantId,
    String merchantOrderReference,
    Instant createdAt,
    Instant updatedAt
) {
    public static ParcelDetailsResponse from(Parcel parcel) {
        return new ParcelDetailsResponse(
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getSenderId(),
            parcel.getReceiverName(),
            parcel.getReceiverPhone(),
            parcel.getPickupAddress(),
            parcel.getDeliveryAddress(),
            parcel.getWeight(),
            parcel.getSize(),
            parcel.getParcelType(),
            parcel.getCharge(),
            parcel.getStatus(),
            parcel.getMerchantId(),
            parcel.getMerchantOrderReference(),
            parcel.getCreatedAt(),
            parcel.getUpdatedAt()
        );
    }
}
