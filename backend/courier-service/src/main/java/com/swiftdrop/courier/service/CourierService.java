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

    public CourierService(CourierRepository couriers) {
        this.couriers = couriers;
    }

    public List<CourierResponse> list() {
        return couriers.findAll(Sort.by("displayName")).stream().map(CourierResponse::from).toList();
    }

    public Optional<CourierResponse> find(UUID courierId) {
        return couriers.findById(courierId).map(CourierResponse::from);
    }
}
