package com.swiftdrop.courier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "couriers")
public class Courier {
    @Id
    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean available;

    protected Courier() { }

    public UUID getCourierId() { return courierId; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public boolean isAvailable() { return available; }
}
