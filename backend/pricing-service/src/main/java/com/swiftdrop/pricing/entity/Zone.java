
package com.swiftdrop.pricing.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "zones")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Zone() {
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { 
        return id; 
    }

    public String getName() {
         return name; 
    }

    public void setName(String name) {
         this.name = name; 
    }

    public String getCity() {
         return city; 
    }

    public void setCity(String city) {
         this.city = city; 
    }

    public String getDistrict() { 
        return district; 
    }

    public void setDistrict(String district) { 
        this.district = district; 
    }

    public boolean isActive() { 
        return active; 
    }

    public void setActive(boolean active) { 
        this.active = active; 
    }

    public Instant getCreatedAt() {
         return createdAt; 
    }

    public Instant getUpdatedAt() {
         return updatedAt; 
    }
}
