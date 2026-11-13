package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import org.springframework.stereotype.Component;

/**
 * Denies courier mutations until Courier Service defines how numeric Identity
 * user IDs map to UUID courier assignments. Never trust a caller-provided UUID.
 */
@Component
public class CourierAssignmentVerifier {
    public boolean isAssigned(Parcel parcel, Long identityUserId) {
        return false;
    }
}
