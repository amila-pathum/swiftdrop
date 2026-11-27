package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.SyncStatus;
import com.swiftdrop.courier.service.CourierDeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/couriers/me")
@PreAuthorize("hasRole('COURIER')")
public class CourierDeliveryController {
    private final CourierDeliveryService deliveries;
    private final com.swiftdrop.courier.service.CourierWorkflowService workflow;
    public CourierDeliveryController(CourierDeliveryService deliveries,com.swiftdrop.courier.service.CourierWorkflowService workflow){this.deliveries=deliveries;this.workflow=workflow;}
    @GetMapping("/deliveries")
    public PageResult<CourierDeliveryService.Delivery> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="active") String filter,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return deliveries.deliveries(CourierController.userId(jwt),filter,page,size,jwt.getTokenValue());
    }
    @GetMapping("/deliveries/{parcelId}")
    public CourierDeliveryService.Delivery detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId){return deliveries.detail(CourierController.userId(jwt),parcelId,jwt.getTokenValue());}
    @RequestMapping(value="/deliveries/{parcelId}/status",method={RequestMethod.POST,RequestMethod.PATCH},consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId,@Valid @RequestBody DeliveryInput input) {
        return result(deliveries.submit(CourierController.userId(jwt),parcelId,input,jwt.getTokenValue()));
    }
    /** Step one of an optional photo: a signed URL the browser PUTs the file to, then sends {@code photoKey} with the status. */
    @PostMapping(value="/deliveries/{parcelId}/photo-uploads",consumes=MediaType.APPLICATION_JSON_VALUE)
    public com.swiftdrop.courier.photo.DeliveryPhotoService.Ticket photoUpload(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId,
        @Valid @RequestBody PhotoUploadInput input) {
        return deliveries.photoUpload(CourierController.userId(jwt),parcelId,input,jwt.getTokenValue());
    }
    private ResponseEntity<?> result(Submission result) {
        if(result.syncStatus()==SyncStatus.REJECTED)return ResponseEntity.status(409).body(Map.of("error",result.message(),"submission",result));
        return ResponseEntity.status(result.syncStatus()==SyncStatus.CONFIRMED?200:202).body(result);
    }
    @GetMapping("/delivery-submissions/{id}")
    public Submission submission(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return deliveries.submission(CourierController.userId(jwt),id);}
    @GetMapping("/deliveries/{parcelId}/tracking")
    public List<Checkpoint> tracking(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId){return deliveries.tracking(CourierController.userId(jwt),parcelId,jwt.getTokenValue());}
    @GetMapping("/deliveries/{parcelId}/proofs")
    public List<Proof> proofs(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId){return deliveries.proofs(CourierController.userId(jwt),parcelId,jwt.getTokenValue());}
    @GetMapping("/deliveries/{parcelId}/status")
    public com.swiftdrop.courier.dto.CourierWorkflowDtos.Activity activity(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId) {
        return workflow.courierActivity(CourierController.userId(jwt),parcelId);
    }
    @GetMapping("/deliveries/{parcelId}/history")
    public List<com.swiftdrop.courier.client.CourierIntegrations.ParcelHistory> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId) {
        return deliveries.detail(CourierController.userId(jwt),parcelId,jwt.getTokenValue()).parcel().history();
    }
}
