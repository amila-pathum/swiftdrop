package com.swiftdrop.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A photo key handed to a courier for one parcel; it can be attached to one status update. */
@Entity
@Table(name = "delivery_photo_uploads")
public class DeliveryPhotoUpload {
    @Id @Column(name = "photo_key", length = 200) public String photoKey;
    @Column(name = "courier_id", nullable = false) public Long courierId;
    @Column(name = "parcel_id", nullable = false) public UUID parcelId;
    @Column(name = "content_type", nullable = false, length = 20) public String contentType;
    @Column(name = "size_bytes", nullable = false) public long sizeBytes;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "used_at") public Instant usedAt;
}
