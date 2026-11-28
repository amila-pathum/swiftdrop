package com.swiftdrop.notification.internal;

import com.swiftdrop.notification.service.EmailNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Account emails requested by Identity Service. 202 when sent, 502 when the mail server refused it. */
@RestController
@RequestMapping("/internal/emails")
public class InternalEmailController {

    private final EmailNotificationService emails;

    public InternalEmailController(EmailNotificationService emails) {
        this.emails = emails;
    }

    public record WelcomeEmail(@NotBlank @Email String email, String name, @NotBlank String role,
            @NotBlank String status, @NotBlank @Pattern(regexp = "https?://.+") String signInUrl) { }

    public record PasswordResetEmail(@NotBlank @Email String email, String name,
            @NotBlank @Pattern(regexp = "https?://.+") String resetUrl, @NotNull Instant expiresAt) { }

    @PostMapping("/welcome")
    public ResponseEntity<Void> welcome(@Valid @RequestBody WelcomeEmail request) {
        boolean sent = emails.sendWelcomeEmail(request.email(), request.name(), request.role(), request.status(),
                request.signInUrl());
        return ResponseEntity.status(sent ? 202 : 502).build();
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> passwordReset(@Valid @RequestBody PasswordResetEmail request) {
        boolean sent = emails.sendPasswordResetEmail(request.email(), request.name(), request.resetUrl(),
                request.expiresAt());
        return ResponseEntity.status(sent ? 202 : 502).build();
    }
}
