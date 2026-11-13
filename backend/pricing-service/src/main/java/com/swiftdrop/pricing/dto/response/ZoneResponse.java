package com.swiftdrop.pricing.dto.response;

import com.swiftdrop.pricing.entity.Zone;
import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(
        UUID id,
        String name,
        String city,
        String district,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getCity(),
                zone.getDistrict(),
                zone.isActive(),
                zone.getCreatedAt(),
                zone.getUpdatedAt()
        );
    }
}