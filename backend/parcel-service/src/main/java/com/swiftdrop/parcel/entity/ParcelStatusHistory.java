package com.swiftdrop.parcel.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel_status_history", indexes = {
    @Index(name = "idx_parcel_status_history_parcel_time", columnList = "parcel_id, changed_at")
})
public class ParcelStatusHistory {
    @Id
        @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Enumerated(EnumType.STRING)
        @Column(name = "previous_status", length = 30)
    private ParcelStatus previousStatus;

    @Enumerated(EnumType.STRING)
        @Column(name = "new_status", nullable = false, length = 30)
    private ParcelStatus newStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected ParcelStatusHistory() { }

    public ParcelStatusHistory(Parcel parcel, ParcelStatus previousStatus,
                               ParcelStatus newStatus, UUID changedBy, String note) {
        this.parcel = parcel;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.note = note;
        this.changedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Parcel getParcel() { return parcel; }
    public ParcelStatus getPreviousStatus() { return previousStatus; }
    public ParcelStatus getNewStatus() { return newStatus; }
    public Instant getChangedAt() { return changedAt; }
    public UUID getChangedBy() { return changedBy; }
    public String getNote() { return note; }
}
