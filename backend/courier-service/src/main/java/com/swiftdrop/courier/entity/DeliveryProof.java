package com.swiftdrop.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_proofs", uniqueConstraints = @UniqueConstraint(columnNames = {"parcel_id", "attempt_no"}))
public class DeliveryProof {
    @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
    @Column(name = "parcel_id", nullable = false) public UUID parcelId;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "courier_id", nullable = false)
    public CourierProfile courier;
    @OneToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "checkpoint_id", nullable = false, unique = true)
    public DeliveryCheckpoint checkpoint;
    @Column(name = "attempt_no", nullable = false) public int attemptNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public DeliveryStatus outcome;
    @Column(name = "recipient_name", length = 150) public String recipientName;
    // Private storage key. Responses construct an authenticated download URL.
    @Column(name = "photo_key", length = 50) public String photoKey;
    @Enumerated(EnumType.STRING) @Column(name = "failure_reason", length = 40) public FailureReason failureReason;
    @Column(length = 1000) public String note;
    @Column(name = "collection_address_snapshot", length = 1000) public String collectionAddressSnapshot;
    @Column(name = "delivery_address_snapshot", length = 1000) public String deliveryAddressSnapshot;
    @Column(name = "recorded_at", nullable = false) public Instant recordedAt;
}
