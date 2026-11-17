package com.swiftdrop.courier.dto;

import com.swiftdrop.courier.entity.Availability;
import com.swiftdrop.courier.entity.VehicleType;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CourierWorkflowDtos {
    private CourierWorkflowDtos() {}
    public record AvailableCourier(Long id,UUID courierReference,String name,VehicleType vehicleType,
        String area,Availability availability,boolean active,long activeDeliveryCount) {}
    public record AssignmentInput(@NotNull UUID submissionId,@NotNull UUID parcelId,
        @NotNull @Positive Long courierId,LocalDate deliveryDate) {}
    public record CancelInput(@NotNull UUID submissionId,@Size(max=500) String note) {}
    public record AssignmentReceipt(UUID assignmentId,UUID parcelId,Long courierId,UUID courierReference,
        Long merchantUserId,int attemptNo,long version,boolean active,LocalDate deliveryDate,Instant assignedAt,String status) {}
    public record Activity(UUID parcelId,String status,UUID assignmentId,long assignmentVersion,int attemptNo,Instant changedAt) {}
}
