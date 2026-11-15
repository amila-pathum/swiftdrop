package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.request.AssignmentRequest;
import com.swiftdrop.dispatch.dto.request.ReassignRequest;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.DeliveryRow;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.PageResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelDetail;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/parcels/{parcelId}")
    public ParcelDetail detail(@PathVariable UUID parcelId) {
        return dispatch.detail(parcelId);
    }

    @GetMapping("/couriers")
    public List<CourierWorkload> couriers() {
        return dispatch.couriers();
    }

    @GetMapping("/assignments")
    public PageResult<AssignmentRow> assignments(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) @Positive Long courierId,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return dispatch.assignments(state, courierId, trackingNumber, page, pageSize);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResult assign(@Valid @RequestBody AssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return dispatch.assign(request.parcelId(), request.courierId(), jwt.getSubject());
    }

    @PostMapping("/assignments/{parcelId}/reassign")
    public AssignmentResult reassign(@PathVariable UUID parcelId, @Valid @RequestBody ReassignRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return dispatch.reassign(parcelId, request.courierId(), jwt.getSubject());
    }

    @PostMapping("/assignments/{parcelId}/cancel")
    public AssignmentResult cancelAssignment(@PathVariable UUID parcelId,
            @AuthenticationPrincipal Jwt jwt) {
        return dispatch.cancelAssignment(parcelId, jwt.getSubject());
    }

    @GetMapping("/deliveries")
    public PageResult<DeliveryRow> deliveries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return dispatch.deliveries(status, trackingNumber, page, pageSize);
    }
}
