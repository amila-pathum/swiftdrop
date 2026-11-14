package com.swiftdrop.parcel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcels", indexes = {
    @Index(name = "idx_parcels_sender", columnList = "sender_id"),
    @Index(name = "idx_parcels_merchant", columnList = "merchant_id"),
    @Index(name = "idx_parcels_status", columnList = "status")
})
public class Parcel {
    @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 40)
    private String trackingNumber;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_name", nullable = false, length = 150)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 30)
    private String receiverPhone;

    @Column(name = "pickup_address", nullable = false, columnDefinition = "text")
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "text")
    private String deliveryAddress;

    @Column(name = "weight", nullable = false, precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(name = "size", nullable = false, length = 30)
    private String size;

    @Column(name = "parcel_type", nullable = false, length = 50)
    private String parcelType;

    @Column(name = "charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal charge;

    @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 30)
    private ParcelStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_order_reference", length = 150)
    private String merchantOrderReference;

    @Column(name = "assigned_courier_id")
    private Long assignedCourierId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Parcel() { }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = ParcelStatus.CREATED;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getParcelType() { return parcelType; }
    public void setParcelType(String parcelType) { this.parcelType = parcelType; }

    public BigDecimal getCharge() { return charge; }
    public void setCharge(BigDecimal charge) { this.charge = charge; }

    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getMerchantOrderReference() { return merchantOrderReference; }
    public void setMerchantOrderReference(String merchantOrderReference) { this.merchantOrderReference = merchantOrderReference; }

    public Long getAssignedCourierId() { return assignedCourierId; }
    public void setAssignedCourierId(Long assignedCourierId) { this.assignedCourierId = assignedCourierId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
