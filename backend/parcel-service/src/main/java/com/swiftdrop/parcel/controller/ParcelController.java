package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.service.ParcelCreationService;
import com.swiftdrop.parcel.service.ParcelStatusUpdateService;
import com.swiftdrop.parcel.dto.request.UpdateParcelStatusRequest;
import com.swiftdrop.parcel.dto.response.ParcelStatusUpdateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {
    private final ParcelCreationService service;
    private final ParcelStatusUpdateService statusUpdates;

    public ParcelController(ParcelCreationService service, ParcelStatusUpdateService statusUpdates) {
        this.service = service;
        this.statusUpdates = statusUpdates;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse create(@Valid @RequestBody CreateParcelRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
            if (userId <= 0 || !userId.toString().equals(jwt.getSubject())) {
                throw new IllegalArgumentException("Non-canonical user ID");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Token subject is not a valid user identifier");
        }
        return service.createCustomerParcel(request, userId);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COURIER', 'DISPATCHER', 'ADMIN')")
    public ParcelStatusUpdateResponse updateStatus(@PathVariable java.util.UUID id,
            @Valid @RequestBody UpdateParcelStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long actorId;
        try {
            actorId = Long.valueOf(jwt.getSubject());
            if (actorId <= 0 || !actorId.toString().equals(jwt.getSubject())) {
                throw new NumberFormatException("Invalid subject");
            }
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user identifier");
        }
        String role = jwt.getClaimAsString("role");
        return statusUpdates.update(id, request, actorId, role);
    }
}
