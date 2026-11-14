package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.Availability;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.service.CourierProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/couriers")
public class CourierController {
    private final CourierProfileService profiles;
    public CourierController(CourierProfileService profiles){this.profiles=profiles;}
    static Long userId(Jwt jwt) {
        try {Long id=Long.valueOf(jwt.getSubject());if(id<=0||!id.toString().equals(jwt.getSubject()))throw new NumberFormatException();return id;}
        catch(NumberFormatException e){throw CourierException.forbidden("Invalid numeric user identifier.");}
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Profile> provision(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProvisionInput input) {
        var result=profiles.provision(input,jwt.getTokenValue());
        return ResponseEntity.status(result.created()?201:200).body(result.profile());
    }
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public PageResult<Profile> list(@RequestParam(required=false) Availability availability,@RequestParam(required=false) @Size(max=100) String area,
        @RequestParam(defaultValue="true") boolean active,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return profiles.list(availability,area,active,page,size);}
    @GetMapping("/me") @PreAuthorize("hasRole('COURIER')")
    public Profile me(@AuthenticationPrincipal Jwt jwt){return Profile.from(profiles.own(userId(jwt)));}
    @PutMapping("/me") @PreAuthorize("hasRole('COURIER')")
    public Profile update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProfileInput input){return profiles.update(userId(jwt),input);}
    @PatchMapping("/me/availability") @PreAuthorize("hasRole('COURIER')")
    public Profile availability(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AvailabilityInput input){return profiles.availability(userId(jwt),input.availability());}
    @PatchMapping("/{id}/active") @PreAuthorize("hasRole('ADMIN')")
    public Profile active(@AuthenticationPrincipal Jwt jwt,@PathVariable @Positive Long id,@Valid @RequestBody ActiveInput input){return profiles.active(id,input.active(),jwt.getTokenValue());}
}
