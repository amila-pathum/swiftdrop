package com.swiftdrop.identity_service.controller;

import com.swiftdrop.identity_service.dto.ChangeRoleRequest;
import com.swiftdrop.identity_service.dto.ChangeStatusRequest;
import com.swiftdrop.identity_service.dto.CreateStaffRequest;
import com.swiftdrop.identity_service.dto.PasswordResetTokenResponse;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.security.AuthenticatedUser;
import com.swiftdrop.identity_service.service.AdminUserService;
import com.swiftdrop.identity_service.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Lists users with pagination, optional search (by name, email, or phone),
     * and optional filtering by role and account status.
     */
    @GetMapping
    public Page<UserResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        String searchTerm = search != null && !search.isBlank() ? search : q;
        return adminUserService.listUsers(searchTerm, name, email, phone, role, status, pageable);
    }

    /** Creates a DISPATCHER or ADMIN account. */
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

    /** Changes a user's role. */
    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        return adminUserService.changeRole(admin.id(), id, request.role());
    }

    @PutMapping("/{id}/role")
    public UserResponse updateRole(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        return adminUserService.changeRole(admin.id(), id, request.role());
    }

    /** Activates, suspends or disables an account. */
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
