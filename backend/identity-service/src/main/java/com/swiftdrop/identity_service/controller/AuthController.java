package com.swiftdrop.identity_service.controller;

import com.swiftdrop.identity_service.dto.GoogleExchangeRequest;
import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.LoginResponse;
import com.swiftdrop.identity_service.dto.RegisterRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google/exchange")
    public LoginResponse exchangeGoogleCode(@Valid @RequestBody GoogleExchangeRequest request) {
        return authService.exchangeGoogleCode(request);
    }
}
