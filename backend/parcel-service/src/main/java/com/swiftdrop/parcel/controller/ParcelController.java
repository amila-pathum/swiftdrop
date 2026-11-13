package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.dto.response.PublicTrackingResponse;
import com.swiftdrop.parcel.service.ParcelService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import com.swiftdrop.parcel.service.ParcelCreationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {
    private final ParcelCreationService service;

    private final ParcelService parcels;

    public ParcelController(ParcelCreationService service, ParcelService parcels) {
        this.service = service;
        this.parcels = parcels;
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<PublicTrackingResponse>
            track(@PathVariable String trackingNumber) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(parcels.track(trackingNumber));
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
