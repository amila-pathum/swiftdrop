package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.client.CourierIntegrations.ParcelHistory;
import com.swiftdrop.courier.dto.CourierDtos.PageResult;
import com.swiftdrop.courier.dto.CourierWorkflowDtos.*;
import com.swiftdrop.courier.entity.VehicleType;
import com.swiftdrop.courier.service.CourierDeliveryService;
import com.swiftdrop.courier.service.CourierWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/couriers")
public class CourierMerchantController {
    private final CourierWorkflowService workflow;
    public CourierMerchantController(CourierWorkflowService workflow) {this.workflow=workflow;}

    @GetMapping("/available") @PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
    public PageResult<AvailableCourier> available(@RequestParam(required=false) @Size(max=100) String area,
            @RequestParam(required=false) VehicleType vehicleType,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return workflow.available(area,vehicleType,page,size);
    }
    @GetMapping("/{id}/availability") @PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
    public AvailableCourier availability(@PathVariable @Positive Long id) {return workflow.availability(id);}

    @PostMapping("/assignments") @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<AssignmentReceipt> assign(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AssignmentInput input) {
        return ResponseEntity.ok(workflow.assign(CourierController.userId(jwt),input));
    }
    @PostMapping("/assignments/{parcelId}/cancel") @PreAuthorize("hasRole('MERCHANT')")
    public AssignmentReceipt cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId,@Valid @RequestBody CancelInput input) {
        return workflow.cancel(CourierController.userId(jwt),parcelId,input);
    }
    @GetMapping("/merchant/deliveries") @PreAuthorize("hasRole('MERCHANT')")
    public PageResult<CourierDeliveryService.Delivery> deliveries(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return workflow.merchantDeliveries(CourierController.userId(jwt),status,page,size);
    }
    @GetMapping("/merchant/deliveries/{parcelId}") @PreAuthorize("hasRole('MERCHANT')")
    public CourierDeliveryService.Delivery detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId) {
        return workflow.merchantDetail(CourierController.userId(jwt),parcelId);
    }
    @GetMapping("/merchant/deliveries/{parcelId}/status") @PreAuthorize("hasRole('MERCHANT')")
    public Activity status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId) {
        return workflow.merchantActivity(CourierController.userId(jwt),parcelId);
    }
    @GetMapping("/merchant/deliveries/{parcelId}/history") @PreAuthorize("hasRole('MERCHANT')")
    public List<ParcelHistory> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId) {
        return workflow.merchantDetail(CourierController.userId(jwt),parcelId).parcel().history();
    }
}
