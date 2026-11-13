package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.service.ParcelCreationService;
import com.swiftdrop.parcel.service.ParcelStatusUpdateService;
import com.swiftdrop.parcel.dto.request.UpdateParcelStatusRequest;
import com.swiftdrop.parcel.dto.response.ParcelStatusUpdateResponse;
import com.swiftdrop.parcel.dto.response.ParcelDetailsResponse;
import com.swiftdrop.parcel.dto.response.ParcelHistoryResponse;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.service.ParcelQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.UUID;
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
    private final ParcelQueryService queryService;

    public ParcelController(ParcelCreationService service, ParcelStatusUpdateService statusUpdates, ParcelQueryService queryService) {
        this.service = service;
        this.statusUpdates = statusUpdates;
        this.queryService = queryService;
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

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<ParcelDetailsResponse> getMyParcels(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return queryService.getMyParcels(parseUserId(jwt),
            createPageRequest(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISPATCHER', 'ADMIN')")
    public ParcelDetailsResponse getParcelById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getParcelById(id, parseUserId(jwt), jwt.getClaimAsString("role"));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISPATCHER', 'ADMIN')")
    public List<ParcelHistoryResponse> getParcelHistory(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getParcelHistory(id, parseUserId(jwt), jwt.getClaimAsString("role"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public Page<ParcelDetailsResponse> getParcelsByStatus(@RequestParam ParcelStatus status,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        parseUserId(jwt);
        return queryService.getParcelsByStatus(status, jwt.getClaimAsString("role"),
            createPageRequest(page, size, Sort.by("createdAt").ascending()));
    }

    private Long parseUserId(Jwt jwt) {
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            if (userId <= 0 || !userId.toString().equals(jwt.getSubject())) {
                throw new NumberFormatException("Invalid user ID");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user identifier");
        }
    }

    private PageRequest createPageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, sort);
    }
}
