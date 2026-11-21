package com.swiftdrop.courier.application;

import com.swiftdrop.courier.entity.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CourierApplicationDtos {
    private CourierApplicationDtos() {}

    /** Sent by the applicant. The user is always the token subject, never a field. */
    public record ApplicationInput(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,30}") String phone,
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 30) String vehicleRegistrationNumber,
        @NotBlank @Size(max = 100) String preferredArea) {}

    public record RejectInput(@NotBlank @Size(max = 500) String reason) {}

    public record ApplicationResponse(
        Long id, Long userId, String name, String phone, VehicleType vehicleType,
        String vehicleRegistrationNumber, String preferredArea, ApplicationStatus status,
        String reviewNote, Instant submittedAt, Instant reviewedAt) {

        static ApplicationResponse from(CourierApplication a) {
            return new ApplicationResponse(a.id, a.userId, a.name, a.phone, a.vehicleType,
                a.vehicleNumber, a.area, a.status, a.rejectionReason, a.createdAt, a.reviewedAt);
        }
    }
}
