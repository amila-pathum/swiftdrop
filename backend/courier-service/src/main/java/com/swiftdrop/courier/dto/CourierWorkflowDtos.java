package com.swiftdrop.courier.dto;

import com.swiftdrop.courier.entity.Availability;
import com.swiftdrop.courier.entity.VehicleType;
import java.time.Instant;
import java.util.UUID;

public final class CourierWorkflowDtos {
    private CourierWorkflowDtos() {}
    public record AvailableCourier(Long id,UUID courierReference,String name,VehicleType vehicleType,
        String area,Availability availability,boolean active,long activeDeliveryCount) {}
    public record Activity(UUID parcelId,String status,UUID assignmentId,long assignmentVersion,int attemptNo,Instant changedAt) {}
}
