package com.swiftdrop.pricing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The rate between two zones within one {@link RateVersion}. It applies in both
 * directions, so each pair is stored once with the smaller zone id as zone A
 * (use {@link #orderedPair}).
 */
@Entity
@Table(name = "route_rates")
public class RouteRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rate_version_id", nullable = false)
    private RateVersion rateVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_a_id", nullable = false)
    private Zone zoneA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_b_id", nullable = false)
    private Zone zoneB;

    @Column(name = "base_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal baseWeightKg;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "additional_kg_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalKgFee;

    @Column(name = "express_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal expressMultiplier;

    public RouteRate() {
    }

    public RouteRate(Zone first, Zone second, BigDecimal baseWeightKg, BigDecimal baseFee,
            BigDecimal additionalKgFee, BigDecimal expressMultiplier) {
        Zone[] pair = orderedPair(first, second);
        this.zoneA = pair[0];
        this.zoneB = pair[1];
        this.baseWeightKg = baseWeightKg;
        this.baseFee = baseFee;
        this.additionalKgFee = additionalKgFee;
        this.expressMultiplier = expressMultiplier;
    }

    /** The two zones in storage order, matching PostgreSQL's ordering of UUIDs. */
    public static Zone[] orderedPair(Zone first, Zone second) {
        return compare(first.getId(), second.getId()) <= 0
            ? new Zone[] {first, second} : new Zone[] {second, first};
    }

    /**
     * Compares UUIDs the way PostgreSQL does (unsigned, byte by byte), which differs
     * from {@link UUID#compareTo} for ids whose high bit is set.
     */
    public static int compare(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high
            : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    /** A copy for a newer version. */
    public RouteRate copy() {
        return new RouteRate(zoneA, zoneB, baseWeightKg, baseFee, additionalKgFee, expressMultiplier);
    }

    public boolean connects(UUID first, UUID second) {
        UUID a = zoneA.getId();
        UUID b = zoneB.getId();
        return (a.equals(first) && b.equals(second)) || (a.equals(second) && b.equals(first));
    }

    public UUID getId() {
        return id;
    }

    public RateVersion getRateVersion() {
        return rateVersion;
    }

    void setRateVersion(RateVersion rateVersion) {
        this.rateVersion = rateVersion;
    }

    public Zone getZoneA() {
        return zoneA;
    }

    public Zone getZoneB() {
        return zoneB;
    }

    public BigDecimal getBaseWeightKg() {
        return baseWeightKg;
    }

    public BigDecimal getBaseFee() {
        return baseFee;
    }

    public BigDecimal getAdditionalKgFee() {
        return additionalKgFee;
    }

    public BigDecimal getExpressMultiplier() {
        return expressMultiplier;
    }
}
