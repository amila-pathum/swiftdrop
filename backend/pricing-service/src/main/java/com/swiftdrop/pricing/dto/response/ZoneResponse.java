package com.swiftdrop.pricing.dto.response;

import com.swiftdrop.pricing.entity.Zone;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ZoneResponse(
        UUID id,
        String name,
        String code,
        List<String> localities,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getCode(),
                List.copyOf(zone.getLocalities()),
                zone.isActive(),
                zone.getCreatedAt(),
                zone.getUpdatedAt()
        );
    }
}
