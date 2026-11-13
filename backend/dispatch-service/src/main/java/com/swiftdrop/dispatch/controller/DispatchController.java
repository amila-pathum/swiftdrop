package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.request.AssignmentRequest;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignedParcel;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.PageResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelDetail;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dispatch")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
@Validated
public class DispatchController {
    private final DispatchService dispatch;

    public DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/parcels/unassigned")
    public PageResult<ParcelRow> unassigned(
            @RequestParam(required = false) String pickupArea,
            @RequestParam(required = false) String dropoffArea,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String size,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return dispatch.unassigned(pickupArea, dropoffArea, from, to, size, page, pageSize, sort, direction);
    }

    @GetMapping("/parcels")
    public PageResult<ParcelRow> parcels(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID courierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return dispatch.parcels(status, courierId, from, to, trackingNumber,
            page, pageSize, sort, direction);
    }

    @GetMapping("/deliveries")
    public PageResult<ParcelRow> deliveries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return dispatch.deliveries(status, trackingNumber, page, pageSize, sort, direction);
    }

    @GetMapping("/parcels/{parcelId}")
    public ParcelDetail detail(@PathVariable UUID parcelId) {
        return dispatch.detail(parcelId);
    }

    @GetMapping("/couriers")
    public List<CourierWorkload> couriers() {
        return dispatch.couriers();
    }

    @GetMapping("/couriers/{courierId}/parcels")
    public List<AssignedParcel> assignedParcels(@PathVariable UUID courierId) {
        return dispatch.assignedParcels(courierId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.OK)
    public AssignmentResult assign(@Valid @RequestBody AssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return dispatch.assign(request.parcelId(), request.courierId(), jwt.getSubject());
    }

    @PostMapping("/assignments/{parcelId}/cancel")
    public AssignmentResult cancelAssignment(@PathVariable UUID parcelId,
            @AuthenticationPrincipal Jwt jwt) {
        return dispatch.cancelAssignment(parcelId, jwt.getSubject());
    }
}
