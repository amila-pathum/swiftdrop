package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.CourierDtos.PageResult;
import com.swiftdrop.courier.dto.CourierWorkflowDtos.AvailableCourier;
import com.swiftdrop.courier.entity.VehicleType;
import com.swiftdrop.courier.service.CourierWorkflowService;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/couriers")
@PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
public class CourierDirectoryController {
    private final CourierWorkflowService workflow;
    public CourierDirectoryController(CourierWorkflowService workflow) { this.workflow=workflow; }

    @GetMapping("/available")
    public PageResult<AvailableCourier> available(@RequestParam(required=false) @Size(max=100) String area,
            @RequestParam(required=false) VehicleType vehicleType,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return workflow.available(area,vehicleType,page,size);
    }

    @GetMapping("/{id}/availability")
    public AvailableCourier availability(@PathVariable @Positive Long id) { return workflow.availability(id); }
}
