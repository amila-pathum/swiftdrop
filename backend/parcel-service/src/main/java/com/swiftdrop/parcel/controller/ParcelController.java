package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.service.ParcelCreationService;
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

    public ParcelController(ParcelCreationService service) {
        this.service = service;
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
}
