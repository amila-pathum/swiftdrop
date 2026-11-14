package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.SyncStatus;
import com.swiftdrop.courier.service.CourierDeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/couriers/me")
@PreAuthorize("hasRole('COURIER')")
public class CourierDeliveryController {
    private final CourierDeliveryService deliveries;
    public CourierDeliveryController(CourierDeliveryService deliveries){this.deliveries=deliveries;}
    @GetMapping("/deliveries")
    public PageResult<CourierDeliveryService.Delivery> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="active") String filter,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return deliveries.deliveries(CourierController.userId(jwt),filter,page,size,jwt.getTokenValue());
    }
    @GetMapping("/deliveries/{parcelId}")
    public CourierDeliveryService.Delivery detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId){return deliveries.detail(CourierController.userId(jwt),parcelId,jwt.getTokenValue());}
    @PostMapping(value="/deliveries/{parcelId}/status",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId,@Valid @RequestBody DeliveryInput input) {
        return result(deliveries.submit(CourierController.userId(jwt),parcelId,input,null,jwt.getTokenValue()));
    }
    @PostMapping(value="/deliveries/{parcelId}/status",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> statusWithPhoto(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId,
        @Valid @RequestPart("data") DeliveryInput input,@RequestPart(value="photo",required=false) MultipartFile photo) {
        return result(deliveries.submit(CourierController.userId(jwt),parcelId,input,photo,jwt.getTokenValue()));
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
    @GetMapping("/deliveries/{parcelId}/proofs/{proofId}/photo")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID parcelId,@PathVariable UUID proofId) {
        Resource resource=deliveries.photo(CourierController.userId(jwt),parcelId,proofId,jwt.getTokenValue());
        boolean png=Objects.requireNonNull(resource.getFilename()).endsWith(".png");
        return ResponseEntity.ok().contentType(png?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(png?"delivery-proof.png":"delivery-proof.jpg").build().toString()).body(resource);
    }
}
