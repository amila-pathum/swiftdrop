package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParcelStatusTransitionValidatorTest {
    private final ParcelStatusTransitionValidator validator = new ParcelStatusTransitionValidator();

    @Test
    void acceptsEveryAllowedTransition() {
        assertTrue(validator.isAllowed(ParcelStatus.CREATED, ParcelStatus.PICKED_UP));
        assertTrue(validator.isAllowed(ParcelStatus.CREATED, ParcelStatus.CANCELLED));
        assertTrue(validator.isAllowed(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT));
        assertTrue(validator.isAllowed(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY));
        assertTrue(validator.isAllowed(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED));
        assertTrue(validator.isAllowed(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
    }

    @Test
    void rejectsAllOtherTransitions() {
        for (ParcelStatus from : ParcelStatus.values()) {
            for (ParcelStatus to : ParcelStatus.values()) {
                if (!validator.isAllowed(from, to)) {
                    assertThrows(IllegalArgumentException.class, () -> validator.validate(from, to));
                }
            }
        }
    }
}
