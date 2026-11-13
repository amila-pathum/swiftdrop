package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.DispatchParcelResponse;
import com.swiftdrop.parcel.dto.response.ParcelReceipt;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.service.ParcelCreationService;
import com.swiftdrop.parcel.service.ParcelService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse cancelParcel(@PathVariable("id") UUID id,
                                       @AuthenticationPrincipal Jwt jwt) {
        return ParcelResponse.from(parcelService.cancelParcel(id, extractUserId(jwt)));
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<byte[]> getReceipt(@PathVariable("id") UUID id,
                                            @AuthenticationPrincipal Jwt jwt) {
        ParcelReceipt receipt = parcelService.generateReceipt(id, extractUserId(jwt));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(receipt.filename()).build());
        return new ResponseEntity<>(receipt.content(), headers, HttpStatus.OK);
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
