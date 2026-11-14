package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierProfile;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.repository.DispatchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "swiftdrop.dispatch.data-source", havingValue = "projection",
    matchIfMissing = true)
public class ProjectionDispatchDataClient implements DispatchDataClient {
    private final DispatchRepository repository;

    public ProjectionDispatchDataClient(DispatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ParcelRow> createdParcels() {
        return repository.createdParcels();
    }

    @Override
    public Optional<ParcelRow> findParcel(UUID parcelId) {
        return repository.findParcel(parcelId);
    }

    @Override
    public Optional<ParcelRow> lockParcel(UUID parcelId) {
        return repository.lockParcel(parcelId);
    }

    @Override
    public List<CourierProfile> couriers() {
        return repository.courierProfiles();
    }

    @Override
    public Optional<CourierProfile> findCourier(UUID courierId) {
        return repository.findCourier(courierId);
    }
}
