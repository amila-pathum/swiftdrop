package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.dto.response.DispatchParcelResponse;
import com.swiftdrop.parcel.entity.ParcelStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CourierAssignmentVerifierTest {
    @Test void numericIdentityMustExactlyMatchTheStoredAssignment() {
        var verifier = new CourierAssignmentVerifier();
        var parcel = new Parcel();
        assertThat(verifier.isAssigned(parcel, 88L)).isFalse();
        parcel.setAssignedCourierId(88L);
        assertThat(verifier.isAssigned(parcel, 88L)).isTrue();
        assertThat(verifier.isAssigned(parcel, 77L)).isFalse();
        assertThat(verifier.isAssigned(parcel, null)).isFalse();
        assertThat(verifier.isAssigned(parcel, 0L)).isFalse();
        parcel.setStatus(ParcelStatus.CREATED);
        var dispatch = DispatchParcelResponse.from(parcel);
        assertThat(dispatch.courierId()).isNull(); // Profile UUID is not derivable from Identity ID.
        assertThat(dispatch.courierUserId()).isEqualTo(88L);
    }
}
