package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelStatus;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class ParcelStatusTransitionValidator {
    private static final Map<ParcelStatus, Set<ParcelStatus>> ALLOWED = Map.of(
        ParcelStatus.CREATED, Set.of(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED),
        ParcelStatus.PICKED_UP, Set.of(ParcelStatus.IN_TRANSIT),
        ParcelStatus.IN_TRANSIT, Set.of(ParcelStatus.OUT_FOR_DELIVERY),
        ParcelStatus.OUT_FOR_DELIVERY, Set.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED)
    );

    public boolean isAllowed(ParcelStatus from, ParcelStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void validate(ParcelStatus from, ParcelStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalArgumentException("Invalid parcel status transition: " + from + " -> " + to);
        }
    }
}
