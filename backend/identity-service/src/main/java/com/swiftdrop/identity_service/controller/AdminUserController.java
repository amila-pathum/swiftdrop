package com.swiftdrop.identity_service.controller;

import com.swiftdrop.identity_service.dto.ChangeStatusRequest;
import com.swiftdrop.identity_service.dto.CreateStaffRequest;
import com.swiftdrop.identity_service.dto.PasswordResetTokenResponse;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.security.AuthenticatedUser;
import com.swiftdrop.identity_service.service.AdminUserService;
import com.swiftdrop.identity_service.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final PasswordResetService passwordResetService;

    public AdminUserController(AdminUserService adminUserService,
            PasswordResetService passwordResetService) {
        this.adminUserService = adminUserService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createStaff(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(adminUserService.createStaff(admin.id(), request));
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable long id) {
        return adminUserService.getUser(id);
    }

    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
        return adminUserService.changeStatus(admin.id(), id, request.status());
    }

    /**
     * Activates a PENDING_APPROVAL merchant or courier. The Merchant or Courier
     * Service forwards the approving admin's own Authorization header.
     */
    @PostMapping("/{id}/activate")
    public UserResponse activate(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id) {
        return adminUserService.activate(admin.id(), id);
    }

    /**
     * Records that the admin rejected an application. The identity account
     * stays PENDING_APPROVAL; application status is in the business service.
     */
    @PostMapping("/{id}/reject")
    public UserResponse reject(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id) {
        return adminUserService.reject(admin.id(), id);
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<PasswordResetTokenResponse> issuePasswordReset(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(passwordResetService.issueFor(admin.id(), id));
    }
}
