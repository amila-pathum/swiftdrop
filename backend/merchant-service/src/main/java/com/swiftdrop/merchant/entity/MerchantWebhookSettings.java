package com.swiftdrop.merchant.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "merchant_webhook_settings")
public class MerchantWebhookSettings {
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
