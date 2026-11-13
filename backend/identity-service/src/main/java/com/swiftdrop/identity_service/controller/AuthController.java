package com.swiftdrop.identity_service.controller;

import com.swiftdrop.identity_service.dto.GoogleExchangeRequest;
import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.LoginResponse;
import com.swiftdrop.identity_service.dto.MessageResponse;
import com.swiftdrop.identity_service.dto.PasswordResetConfirmRequest;
import com.swiftdrop.identity_service.dto.PasswordResetRequest;
import com.swiftdrop.identity_service.dto.RegisterRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.security.AuthenticatedUser;
import com.swiftdrop.identity_service.service.AuthService;
import com.swiftdrop.identity_service.service.PasswordResetService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /** Customer sign-up. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Merchant sign-up: the identity account only, awaiting approval. */
    @PostMapping("/register/merchant")
    public ResponseEntity<UserResponse> registerMerchant(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(authService.registerMerchant(request));
    }

    /** Courier sign-up: the identity account only, awaiting approval. */
    @PostMapping("/register/courier")
    public ResponseEntity<UserResponse> registerCourier(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(authService.registerCourier(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google/exchange")
    public LoginResponse exchangeGoogleCode(@Valid @RequestBody GoogleExchangeRequest request) {
        return authService.exchangeGoogleCode(request);
    }

    /** Invalidates every access token of the signed-in user. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Asks for a reset link by email. Answers 501 until email delivery exists;
     * then 202 whether or not the email is registered.
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestByEmail(request.email());
        return ResponseEntity.accepted().body(new MessageResponse(
            "If an account exists for this email, a password reset link has been sent"));
    }

    /** Sets a new password with a reset token. */
    @PostMapping("/password-reset/confirm")
    public MessageResponse confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.token(), request.newPassword());
        return new MessageResponse("Password has been reset");
    }
}
