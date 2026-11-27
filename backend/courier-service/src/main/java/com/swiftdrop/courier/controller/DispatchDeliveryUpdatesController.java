package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.CourierDtos.DispatchUpdate;
import com.swiftdrop.courier.service.CourierDeliveryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Courier evidence (notes, recipient, failure reason, photos) for the dispatcher's delivery detail. */
@RestController
@RequestMapping("/api/couriers/dispatch")
@PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
public class DispatchDeliveryUpdatesController {
    private final CourierDeliveryService deliveries;
    public DispatchDeliveryUpdatesController(CourierDeliveryService deliveries) { this.deliveries=deliveries; }

    @GetMapping("/parcels/{parcelId}/updates")
    public List<DispatchUpdate> updates(@PathVariable UUID parcelId) { return deliveries.dispatchUpdates(parcelId); }
}
