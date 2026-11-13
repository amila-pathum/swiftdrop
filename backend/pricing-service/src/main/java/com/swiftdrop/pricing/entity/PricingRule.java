
package com.swiftdrop.pricing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_rules")
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "price_per_kg", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "express_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal expressMultiplier;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PricingRule() {
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

    public Zone getZone() {
         return zone; 
    }

    public void setZone(Zone zone) {
         this.zone = zone; 
    }

    public BigDecimal getBaseFee() {
         return baseFee; 
    }

    public void setBaseFee(BigDecimal baseFee) {
         this.baseFee = baseFee; 
    }

    public BigDecimal getPricePerKg() { 
        return pricePerKg; 
    }
    public void setPricePerKg(BigDecimal pricePerKg) { 
        this.pricePerKg = pricePerKg; 
    }

    public BigDecimal getExpressMultiplier() {
         return expressMultiplier; 
    }

    public void setExpressMultiplier(BigDecimal expressMultiplier) {
         this.expressMultiplier = expressMultiplier; 
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
