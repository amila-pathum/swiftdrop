package com.swiftdrop.courier.application;

import com.swiftdrop.courier.application.CourierApplicationDtos.ApplicationInput;
import com.swiftdrop.courier.application.CourierApplicationDtos.ApplicationResponse;
import com.swiftdrop.courier.application.CourierApplicationDtos.RejectInput;
import com.swiftdrop.courier.exception.CourierException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/couriers")
public class CourierApplicationController {
    // A courier awaiting approval holds APPLICANT_COURIER; an approved one ROLE_COURIER.
    private static final String APPLICANT = "hasAnyAuthority('APPLICANT_COURIER','ROLE_COURIER')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final CourierApplicationService applications;

    public CourierApplicationController(CourierApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping("/applications")
    @PreAuthorize(APPLICANT)
    public ResponseEntity<ApplicationResponse> submit(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplicationInput input) {
        return ResponseEntity.status(201).body(applications.submit(userId(jwt), input));
    }

    @GetMapping("/me/application")
    @PreAuthorize(APPLICANT)
    public ApplicationResponse own(@AuthenticationPrincipal Jwt jwt) {
        return applications.own(userId(jwt));
    }

    @GetMapping("/admin/applications")
    @PreAuthorize(ADMIN)
    public List<ApplicationResponse> list(@RequestParam(required = false) ApplicationStatus status) {
        return applications.list(status);
    }

    @GetMapping("/admin/applications/{id}")
    @PreAuthorize(ADMIN)
    public ApplicationResponse get(@PathVariable @Positive Long id) {
        return applications.get(id);
    }

    @PatchMapping("/admin/applications/{id}/approve")
    @PreAuthorize(ADMIN)
    public ApplicationResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return applications.approve(id, userId(jwt), jwt.getTokenValue());
    }

    @PatchMapping("/admin/applications/{id}/reject")
    @PreAuthorize(ADMIN)
    public ApplicationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
            @Valid @RequestBody RejectInput input) {
        return applications.reject(id, userId(jwt), input.reason());
    }

    private static Long userId(Jwt jwt) {
        try {
            Long id = Long.valueOf(jwt.getSubject());
            if (id <= 0 || !id.toString().equals(jwt.getSubject())) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw CourierException.forbidden("Invalid numeric user identifier.");
        }
    }
}
