package com.swiftdrop.courier.application;

import com.swiftdrop.courier.entity.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * What a courier submitted when applying. It is kept separate from
 * {@code CourierProfile}: the profile only exists once an administrator has
 * approved the application.
 */
@Entity
@Table(name = "courier_applications")
public class CourierApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    Long userId;
    @Column(nullable = false, length = 100) String name;
    @Column(nullable = false, length = 30) String phone;
    @Enumerated(EnumType.STRING) @Column(name = "vehicle_type", nullable = false, length = 30)
    VehicleType vehicleType;
    @Column(name = "vehicle_number", nullable = false, length = 30) String vehicleNumber;
    @Column(nullable = false, length = 100) String area;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    ApplicationStatus status = ApplicationStatus.PENDING;
    @Column(name = "rejection_reason", length = 500) String rejectionReason;
    @Column(name = "reviewed_by") Long reviewedBy;
    @Column(name = "reviewed_at") Instant reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version long version;

    @PrePersist void create() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public ApplicationStatus getStatus() { return status; }
}
