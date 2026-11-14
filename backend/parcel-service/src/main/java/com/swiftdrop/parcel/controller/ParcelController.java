package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.request.UpdateParcelStatusRequest;
import com.swiftdrop.parcel.dto.response.DispatchParcelResponse;
import com.swiftdrop.parcel.dto.response.ParcelDetailsResponse;
import com.swiftdrop.parcel.dto.response.ParcelHistoryResponse;
import com.swiftdrop.parcel.dto.response.ParcelReceipt;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.dto.response.ParcelStatusUpdateResponse;
import com.swiftdrop.parcel.dto.response.PublicTrackingResponse;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.service.ParcelCreationService;
import com.swiftdrop.parcel.service.ParcelQueryService;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusUpdateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
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
    private final ParcelCreationService service;
    private final ParcelStatusUpdateService statusUpdates;
    private final ParcelQueryService queryService;
    private final ParcelService parcelService;

    public ParcelController(ParcelCreationService service, ParcelStatusUpdateService statusUpdates, ParcelQueryService queryService, ParcelService parcelService) {
        this.service = service;
        this.statusUpdates = statusUpdates;
        this.queryService = queryService;
        this.parcelService = parcelService;
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<PublicTrackingResponse>
            track(@PathVariable String trackingNumber) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(parcelService.track(trackingNumber));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse create(@Valid @RequestBody CreateParcelRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.createCustomerParcel(request, parseUserId(jwt), jwt.getTokenValue());
    }

    @PostMapping("/merchant")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public ParcelResponse createMerchant(@Valid @RequestBody CreateParcelRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.createMerchantParcel(request, parseUserId(jwt), jwt.getTokenValue());
    }

    @GetMapping("/merchant")
    @PreAuthorize("hasRole('MERCHANT')")
    public Page<ParcelDetailsResponse> getMerchantParcels(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return queryService.getMerchantParcels(parseUserId(jwt),
            createPageRequest(page, size, Sort.by("createdAt").descending()));
    }

    @PatchMapping("/{id}/courier")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ParcelResponse assignCourier(@PathVariable UUID id,
            @Valid @RequestBody com.swiftdrop.parcel.dto.request.AssignCourierRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        parseUserId(jwt);
        return ParcelResponse.from(parcelService.assignCourier(id, request.courierUserId()));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('COURIER', 'DISPATCHER', 'ADMIN')")
    public ParcelStatusUpdateResponse updateStatus(@PathVariable java.util.UUID id,
            @Valid @RequestBody UpdateParcelStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
        Long actorId = parseUserId(jwt);
        String role = jwt.getClaimAsString("role");
        UUID idempotencyKey = parseIdempotencyKey(idempotencyHeader, request.submissionId());
        return statusUpdates.update(id, request, actorId, role, idempotencyKey);
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
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'DISPATCHER', 'ADMIN')")
    public ParcelDetailsResponse getParcelById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getParcelById(id, parseUserId(jwt), jwt.getClaimAsString("role"));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'DISPATCHER', 'ADMIN')")
    public List<ParcelHistoryResponse> getParcelHistory(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getParcelHistory(id, parseUserId(jwt), jwt.getClaimAsString("role"));
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<byte[]> getReceipt(@PathVariable("id") UUID id,
                                            @AuthenticationPrincipal Jwt jwt) {
        ParcelReceipt receipt = parcelService.generateReceipt(id, parseUserId(jwt));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(receipt.filename()).build());
        return new ResponseEntity<>(receipt.content(), headers, HttpStatus.OK);
    }

    /** Parses and validates the JWT subject as a UUID. Shared by all endpoints. */
    private UUID extractUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Invalid authenticated user identifier");
        }
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
            Long userId = Long.valueOf(jwt == null ? null : jwt.getSubject());
            if (userId <= 0 || !userId.toString().equals(jwt.getSubject())) {
                throw new NumberFormatException("Invalid user ID");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user identifier");
        }
    }

    private UUID parseIdempotencyKey(String header, UUID submissionId) {
        if (header == null || header.isBlank()) {
            if (submissionId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required when submissionId is supplied");
            }
            return null;
        }
        try {
            UUID key = UUID.fromString(header.trim());
            if (submissionId != null && !submissionId.equals(key)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key must match submissionId");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Idempotency-Key must be a UUID");
        }
    }

    private PageRequest createPageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, sort);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse cancelParcel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ParcelResponse.from(parcelService.cancelParcel(id, parseUserId(jwt)));
    }

    // Separate projection contract: the Phase 4 endpoints retain their paginated DTOs.
    @GetMapping("/dispatch")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public List<DispatchParcelResponse> listForDispatch(
            @RequestParam(required = false) String status, @AuthenticationPrincipal Jwt jwt) {
        parseUserId(jwt);
        ParcelStatus parcelStatus;
        try {
            parcelStatus = status == null || status.isBlank() ? null
                : ParcelStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parcel status");
        }
        return parcelService.listParcels(parcelStatus).stream().map(DispatchParcelResponse::from).toList();
    }

    @GetMapping("/dispatch/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public DispatchParcelResponse findForDispatch(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        parseUserId(jwt);
        return parcelService.findParcel(id).map(DispatchParcelResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
    }
}
