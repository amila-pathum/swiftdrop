package com.swiftdrop.identity_service.controller;

import com.swiftdrop.identity_service.dto.ChangePasswordRequest;
import com.swiftdrop.identity_service.dto.LoginResponse;
import com.swiftdrop.identity_service.dto.UpdateProfileRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.security.AuthenticatedUser;
import com.swiftdrop.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getCurrentUser(principal.id());
    }

    @PutMapping("/me")
    public UserResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.id(), request);
    }

    @PutMapping("/me/change-password")
    public LoginResponse changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        return userService.changePassword(principal.id(), request);
    }
}
