package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierProfile;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchDataClient {
    List<ParcelRow> createdParcels();
    Optional<ParcelRow> findParcel(UUID parcelId);
    default Optional<ParcelRow> lockParcel(UUID parcelId) {
        return Optional.empty();
    }
    List<CourierProfile> couriers();
    Optional<CourierProfile> findCourier(UUID courierId);
}
