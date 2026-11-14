package com.swiftdrop.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "courier_profiles")
public class CourierProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    public Long userId;
    @Column(nullable = false, length = 100) public String name;
    @Column(nullable = false, length = 30) public String phone;
    @Enumerated(EnumType.STRING) @Column(name = "vehicle_type", nullable = false, length = 30)
    public VehicleType vehicleType;
    @Column(name = "vehicle_number", nullable = false, length = 30) public String vehicleNumber;
    @Column(nullable = false, length = 100) public String area;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    public Availability availability = Availability.OFFLINE;
    @Column(nullable = false) public boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Version public long version;
    @PrePersist void create() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
