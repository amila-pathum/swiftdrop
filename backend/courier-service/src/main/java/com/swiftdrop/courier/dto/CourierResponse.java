package com.swiftdrop.courier.dto;

import com.swiftdrop.courier.entity.Courier;

import java.util.UUID;

public record CourierResponse(UUID courierId, String name, boolean active, boolean available) {
    public static CourierResponse from(Courier courier) {
        return new CourierResponse(courier.getCourierId(), courier.getDisplayName(),
            courier.isActive(), courier.isAvailable());
    }
}
