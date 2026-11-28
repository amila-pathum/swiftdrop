package com.swiftdrop.courier.dto;

import com.swiftdrop.courier.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CourierDtos {
    private CourierDtos() {}
    public record ProfileInput(
        @NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="[+0-9 ()-]{7,30}") String phone,
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max=30) String vehicleNumber,
        @NotBlank @Size(max=100) String area) {}
    public record ProvisionInput(@NotNull @Positive Long userId, @NotNull @jakarta.validation.Valid ProfileInput profile) {}
    public record AvailabilityInput(@NotNull Availability availability) {}
    public record ActiveInput(@NotNull Boolean active) {}
    public record Profile(Long id, Long userId, String name, String phone, VehicleType vehicleType,
                          String vehicleNumber, String area, Availability availability, boolean active,
                          Instant createdAt, Instant updatedAt, long version) {
        public static Profile from(CourierProfile p) {
            return new Profile(p.id,p.userId,p.name,p.phone,p.vehicleType,p.vehicleNumber,p.area,
                p.availability,p.active,p.createdAt,p.updatedAt,p.version);
        }
    }
    public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record DeliveryInput(
        @NotNull UUID submissionId, @NotNull DeliveryStatus status,
        @Size(max=150) String recipientName, FailureReason failureReason,
        @Size(max=1000) String note, @Size(max=500) String locationDescription,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer=3,fraction=6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer=3,fraction=6) BigDecimal longitude,
        @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal accuracyMeters,
        @PastOrPresent Instant capturedAt,
        @Size(max=200) String photoKey) {}
    /** Asks for a signed upload URL before the status update is sent. */
    public record PhotoUploadInput(@NotBlank @Size(max=20) String contentType, @Positive long sizeBytes) {}
    public record Checkpoint(UUID id, UUID parcelId, int attemptNo, DeliveryStatus status,
        String plannedAddressSnapshot, String locationDescription, BigDecimal latitude,
        BigDecimal longitude, BigDecimal accuracyMeters, Instant capturedAt, Instant recordedAt, String note, String photoUrl) {
        public static Checkpoint from(DeliveryCheckpoint c, String photoUrl) {
            return new Checkpoint(c.id,c.parcelId,c.attemptNo,c.status,c.plannedAddressSnapshot,
                c.locationDescription,c.latitude,c.longitude,c.accuracyMeters,c.capturedAt,c.recordedAt,c.note,photoUrl);
        }
    }
    public record Proof(UUID id, UUID parcelId, int attemptNo, DeliveryStatus outcome, String recipientName,
        String photoUrl, FailureReason failureReason, String note, String collectionAddressSnapshot,
        String deliveryAddressSnapshot, Checkpoint attemptLocation, Instant recordedAt) {
        /** {@code photoUrl} is the signed URL of the checkpoint's photo, or null. */
        public static Proof from(DeliveryProof p, String photoUrl) {
            return new Proof(p.id,p.parcelId,p.attemptNo,p.outcome,p.recipientName,photoUrl,p.failureReason,
                p.note,p.collectionAddressSnapshot,p.deliveryAddressSnapshot,Checkpoint.from(p.checkpoint,photoUrl),p.recordedAt);
        }
    }
    /** One confirmed courier update on a parcel, as dispatchers see it. */
    public record DispatchUpdate(UUID id, DeliveryStatus status, int attemptNo, Instant recordedAt, Long courierId,
        String courierName, String note, String recipientName, FailureReason failureReason, String photoUrl) {}
    public record Submission(UUID submissionId, UUID parcelId, SyncStatus syncStatus, String message) {}
}
