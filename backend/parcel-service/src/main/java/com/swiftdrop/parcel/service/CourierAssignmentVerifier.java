package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import org.springframework.stereotype.Component;

/** Assignment stores an Identity user ID, never a Courier Service profile UUID. */
@Component
public class CourierAssignmentVerifier {
    public boolean isAssigned(Parcel parcel, Long identityUserId) {
        return identityUserId != null && identityUserId > 0
            && identityUserId.equals(parcel.getAssignedCourierId());
    }
}
