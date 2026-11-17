package com.swiftdrop.merchant.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "merchant_webhook_settings")
public class MerchantWebhookSettings {
    public static final java.util.Set<String> ALL_EVENTS = java.util.Set.of(
            "CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED");
    @Column(name = "subscribed_events", nullable = false, length = 200)
    private String subscribedEvents = String.join(",", new java.util.TreeSet<>(ALL_EVENTS));

    public java.util.Set<String> getEvents() {
        return subscribedEvents.isEmpty() ? java.util.Set.of() : java.util.Set.copyOf(java.util.Arrays.asList(subscribedEvents.split(",")));
    }
    public void setEvents(java.util.Set<String> events) {
        if (!ALL_EVENTS.containsAll(events)) throw new IllegalArgumentException("Unknown webhook event");
        subscribedEvents = String.join(",", new java.util.TreeSet<>(events));
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    private Merchant merchant;

    @Column(name = "webhook_url", nullable = false, length = 2048)
    private String webhookUrl;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "encrypted_signing_secret", nullable = false, length = 1024)
    private String encryptedSigningSecret;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    public void markUpdated() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEncryptedSigningSecret() { return encryptedSigningSecret; }
    public void setEncryptedSigningSecret(String encryptedSigningSecret) {
        this.encryptedSigningSecret = encryptedSigningSecret;
    }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
