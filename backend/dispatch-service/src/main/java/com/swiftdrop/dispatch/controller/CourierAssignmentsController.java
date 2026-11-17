package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierAssignment;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.service.DispatchService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Hand-off to courier-service (Member 6): the assignments of one courier
 * profile. Courier-service calls it with the courier's own token, so COURIER is
 * allowed here, limited to the caller's own assignments.
 */
@RestController
@Validated
public class CourierAssignmentsController {
    private final DispatchService dispatch;

    public CourierAssignmentsController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/api/dispatch/couriers/{courierId}/assignments")
    @PreAuthorize("hasAnyRole('COURIER', 'DISPATCHER', 'ADMIN')")
    public List<CourierAssignment> assignments(@PathVariable @Positive Long courierId,
            @AuthenticationPrincipal Jwt jwt) {
        boolean courier = "COURIER".equals(jwt.getClaimAsString("role"));
        Long callerUserId = null;
        if (courier) {
            try {
                callerUserId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException exception) {
                throw new DispatchException(HttpStatus.FORBIDDEN, "Access is denied");
            }
        }
        return dispatch.courierAssignments(courierId, callerUserId, courier);
    }
}
