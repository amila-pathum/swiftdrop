package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
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
    private final ParcelCreationService creationService;
    private final ParcelService parcelService;

    public ParcelController(ParcelCreationService creationService, ParcelService parcelService) {
        this.creationService = creationService;
        this.parcelService = parcelService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse create(@Valid @RequestBody CreateParcelRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return creationService.createCustomerParcel(request, extractUserId(jwt));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ParcelResponse> getMyParcels(@AuthenticationPrincipal Jwt jwt) {
        return parcelService.getMyParcels(extractUserId(jwt))
                .stream()
                .map(ParcelResponse::from)
                .toList();
    }

    /** Parses and validates the JWT subject as a UUID. Shared by all endpoints. */
    private UUID extractUserId(Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            if (!userId.toString().equalsIgnoreCase(jwt.getSubject())) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Token subject is not a valid user identifier");
        }
    }
}
