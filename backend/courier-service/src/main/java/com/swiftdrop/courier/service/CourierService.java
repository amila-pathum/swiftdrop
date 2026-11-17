package com.swiftdrop.courier.service;

import com.swiftdrop.courier.dto.CourierResponse;
import com.swiftdrop.courier.repository.CourierRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourierService {
    private final CourierRepository couriers;
    private final com.swiftdrop.courier.repository.CourierProfileRepository profiles;

    public CourierService(CourierRepository couriers,com.swiftdrop.courier.repository.CourierProfileRepository profiles) {
        this.couriers = couriers;
        this.profiles = profiles;
    }

    public List<CourierResponse> list() {
        var result = new java.util.LinkedHashMap<UUID,CourierResponse>();
        couriers.findAll(Sort.by("displayName")).forEach(c -> result.put(c.getCourierId(),CourierResponse.from(c)));
        profiles.findAll(Sort.by("name")).forEach(p -> result.put(p.dispatchCourierId,
            new CourierResponse(p.dispatchCourierId,p.name,p.active,p.availability == com.swiftdrop.courier.entity.Availability.AVAILABLE,p.id,p.userId)));
        return result.values().stream().sorted(java.util.Comparator.comparing(CourierResponse::name)).toList();
    }

    public Optional<CourierResponse> find(UUID courierId) {
        var profile = profiles.findByDispatchCourierId(courierId);
        if (profile.isPresent()) {
            var p=profile.get();
            return Optional.of(new CourierResponse(p.dispatchCourierId,p.name,p.active,p.availability == com.swiftdrop.courier.entity.Availability.AVAILABLE,p.id,p.userId));
        }
        return couriers.findById(courierId).map(CourierResponse::from);
    }
}
