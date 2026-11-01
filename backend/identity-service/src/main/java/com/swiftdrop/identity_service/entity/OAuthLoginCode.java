package com.swiftdrop.identity_service.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A short-lived, single-use code issued after a successful OAuth sign-in and
 * exchanged by the frontend for a SwiftDrop JWT. Only the code's SHA-256 hash
 * is stored.
 */
@Entity
@Table(
    name = "oauth_login_codes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_oauth_login_codes_code_hash", columnNames = "code_hash")
    }
)
public class OAuthLoginCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // S256 hash of the verifier the frontend must present when exchanging.
    @Column(name = "client_challenge", nullable = false, length = 64)
    private String clientChallenge;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthLoginCode() {
    }

    public OAuthLoginCode(String codeHash, User user, String clientChallenge,
                          Instant createdAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.user = user;
        this.clientChallenge = clientChallenge;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public User getUser() {
        return user;
    }

    public String getClientChallenge() {
        return clientChallenge;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
