package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.CourierResponse;
import com.swiftdrop.courier.service.CourierService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/couriers")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
public class CourierController {
    private final CourierService couriers;

    public CourierController(CourierService couriers) {
        this.couriers = couriers;
    }

    @GetMapping
    public List<CourierResponse> list() {
        return couriers.list();
    }

    @GetMapping("/{courierId}")
    public CourierResponse find(@PathVariable UUID courierId) {
        return couriers.find(courierId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier not found"));
    }
}
