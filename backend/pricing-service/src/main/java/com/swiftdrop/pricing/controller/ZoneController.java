package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.request.ZoneRequest;
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

    /** Active zones for any signed-in user (booking dropdowns); ADMIN may include disabled ones. */
    @GetMapping
    @PreAuthorize("!#includeInactive or hasRole('ADMIN')")
    public List<ZoneResponse> getZones(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return (includeInactive ? zoneService.getAllZones() : zoneService.getActiveZones())
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
    public ZoneResponse createZone(@Valid @RequestBody ZoneRequest request) {
        return ZoneResponse.from(zoneService.createZone(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse updateZone(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return ZoneResponse.from(zoneService.updateZone(id, request));
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse disableZone(@PathVariable UUID id) {
        return ZoneResponse.from(zoneService.disableZone(id));
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse enableZone(@PathVariable UUID id) {
        return ZoneResponse.from(zoneService.enableZone(id));
    }
}
