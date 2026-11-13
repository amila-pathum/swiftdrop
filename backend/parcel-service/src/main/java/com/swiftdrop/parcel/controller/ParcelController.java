package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.DispatchParcelResponse;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.service.ParcelCreationService;
import com.swiftdrop.parcel.service.ParcelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {
    private final ParcelCreationService service;
    private final ParcelService parcelService;

    public ParcelController(ParcelCreationService service, ParcelService parcelService) {
        this.service = service;
        this.parcelService = parcelService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public List<DispatchParcelResponse> list(@RequestParam(required = false) String status) {
        ParcelStatus parcelStatus;
        try {
            parcelStatus = status == null || status.isBlank() ? null
                : ParcelStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parcel status");
        }
        return parcelService.listParcels(parcelStatus).stream()
            .map(DispatchParcelResponse::from).toList();
    }

    @GetMapping("/{parcelId}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public DispatchParcelResponse find(@PathVariable UUID parcelId) {
        return parcelService.findParcel(parcelId).map(DispatchParcelResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse create(@Valid @RequestBody CreateParcelRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
            if (!userId.toString().equalsIgnoreCase(jwt.getSubject())) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Token subject is not a valid user identifier");
        }
        return service.createCustomerParcel(request, userId);
    }
}
