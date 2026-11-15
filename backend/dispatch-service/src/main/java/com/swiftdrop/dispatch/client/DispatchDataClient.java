package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and updates data owned by parcel-service and courier-service through
 * their APIs. Dispatch never reads another service's database.
 */
public interface DispatchDataClient {
    /** Parcels in status CREATED. */
    List<ParcelRow> createdParcels();

    Optional<ParcelRow> findParcel(UUID parcelId);

    /** Every active courier profile, whatever its availability. */
    List<CourierData> couriers();

    Optional<CourierData> findCourier(Long courierProfileId);

    /** Records the courier on the parcel in parcel-service, so that courier may update its status. */
    void assignParcelCourier(UUID parcelId, Long courierUserId);

    /** A courier-service profile (CourierDtos.Profile); {@code id} is the profile id. */
    record CourierData(Long id, Long userId, String name, String availability, boolean active) {
        public boolean available() {
            return active && "AVAILABLE".equals(availability);
        }
    }
}
