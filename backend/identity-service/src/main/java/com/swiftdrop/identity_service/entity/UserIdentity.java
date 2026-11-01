package com.swiftdrop.identity_service.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * An external sign-in (e.g. Google) linked to a SwiftDrop user. Identified by
 * the provider's stable subject id, never by email, which can change.
 */
@Entity
@Table(
    name = "user_identities",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_identities_provider_subject",
            columnNames = {"provider", "provider_subject"}
        ),
        @UniqueConstraint(
            name = "uk_user_identities_user_provider",
            columnNames = {"user_id", "provider"}
        )
    }
)
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    // Provider email when the identity was linked; informational only.
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserIdentity() {
    }

    public UserIdentity(User user, AuthProvider provider, String providerSubject, String email) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
