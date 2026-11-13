package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.request.CreateZoneRequest;
import com.swiftdrop.pricing.dto.request.UpdateZoneRequest;
import com.swiftdrop.pricing.dto.response.ZoneResponse;
import com.swiftdrop.pricing.service.ZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @GetMapping
    public List<ZoneResponse> getActiveZones() {
        return zoneService.getActiveZones()
                .stream()
                .map(ZoneResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ZoneResponse getZoneById(@PathVariable UUID id) {
        return ZoneResponse.from(zoneService.getZoneById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse createZone(
            @Valid @RequestBody CreateZoneRequest request) {

        return ZoneResponse.from(
                zoneService.createZone(
                        request.name(),
                        request.city(),
                        request.district()
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse updateZone(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateZoneRequest request) {

        return ZoneResponse.from(
                zoneService.updateZone(
                        id,
                        request.name(),
                        request.city(),
                        request.district()
                )
        );
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse disableZone(@PathVariable UUID id) {
        return ZoneResponse.from(zoneService.disableZone(id));
    }
}