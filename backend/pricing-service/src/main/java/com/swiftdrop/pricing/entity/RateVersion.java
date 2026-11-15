package com.swiftdrop.pricing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One published set of rates. Publishing never edits a version; it creates the next
 * one, so earlier versions remain as history. Quotes always use the latest version.
 */
@Entity
@Table(name = "rate_versions")
public class RateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private int version;

    @Column(name = "small_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal smallSurcharge;

    @Column(name = "medium_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal mediumSurcharge;

    @Column(name = "large_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal largeSurcharge;

    @Column(name = "published_by", nullable = false, length = 100, updatable = false)
    private String publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @OneToMany(mappedBy = "rateVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteRate> routes = new ArrayList<>();

    public RateVersion() {
    }

    public RateVersion(int version, BigDecimal smallSurcharge, BigDecimal mediumSurcharge,
            BigDecimal largeSurcharge, String publishedBy) {
        this.version = version;
        this.smallSurcharge = smallSurcharge;
        this.mediumSurcharge = mediumSurcharge;
        this.largeSurcharge = largeSurcharge;
        this.publishedBy = publishedBy;
    }

    @PrePersist
    void beforeInsert() {
        publishedAt = Instant.now();
    }

    public BigDecimal surchargeFor(ParcelSize size) {
        return switch (size) {
            case SMALL -> smallSurcharge;
            case MEDIUM -> mediumSurcharge;
            case LARGE -> largeSurcharge;
        };
    }

    public void addRoute(RouteRate route) {
        route.setRateVersion(this);
        routes.add(route);
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public BigDecimal getSmallSurcharge() {
        return smallSurcharge;
    }

    public BigDecimal getMediumSurcharge() {
        return mediumSurcharge;
    }

    public BigDecimal getLargeSurcharge() {
        return largeSurcharge;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public List<RouteRate> getRoutes() {
        return routes;
    }
}
