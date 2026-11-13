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

/**
 * Account administration. ADMIN only: enforced here and, for the whole
 * {@code /api/admin/**} prefix, in SecurityConfig.
 */
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

    /** Creates a DISPATCHER or ADMIN account. */
    @PostMapping
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(adminUserService.createStaff(request));
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable long id) {
        return adminUserService.getUser(id);
    }

    /** Activates, suspends or disables an account. */
    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
        return adminUserService.changeStatus(admin.id(), id, request.status());
    }

    /** Issues a one-time password reset token for the administrator to pass on. */
    @PostMapping("/{id}/password-reset")
    public ResponseEntity<PasswordResetTokenResponse> issuePasswordReset(@PathVariable long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(passwordResetService.issueFor(id));
    }
}
