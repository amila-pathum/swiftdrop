package com.swiftdrop.courier.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_checkpoints")
public class DeliveryCheckpoint {
    // The caller's submission UUID is also the stable remote idempotency key.
    @Id public UUID id;
    @Column(name = "parcel_id", nullable = false) public UUID parcelId;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "courier_id", nullable = false)
    public CourierProfile courier;
    @Column(name = "assignment_id", nullable = false) public UUID assignmentId;
    @Column(name = "assignment_version", nullable = false) public long assignmentVersion;
    @Column(name = "attempt_no", nullable = false) public int attemptNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public DeliveryStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "sync_status", nullable = false, length = 20)
    public SyncStatus syncStatus = SyncStatus.PENDING;
    @Column(name = "planned_address_snapshot", length = 1000) public String plannedAddressSnapshot;
    @Column(name = "location_description", length = 500) public String locationDescription;
    @Column(precision = 9, scale = 6) public BigDecimal latitude;
    @Column(precision = 9, scale = 6) public BigDecimal longitude;
    @Column(name = "accuracy_meters", precision = 10, scale = 2) public BigDecimal accuracyMeters;
    @Column(name = "captured_at") public Instant capturedAt;
    @Column(name = "recorded_at", nullable = false) public Instant recordedAt;
    @Column(length = 1000) public String note;
    @Column(nullable = false, length = 64) public String fingerprint;
    @Column(name = "lease_until") public Instant leaseUntil;
    // Nullable version makes Spring Data persist a new caller-supplied UUID,
    // rather than merging it into a concurrently created submission.
    @Version public Long version;
}
