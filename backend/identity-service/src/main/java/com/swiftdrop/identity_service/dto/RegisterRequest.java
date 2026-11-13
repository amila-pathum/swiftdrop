package com.swiftdrop.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of the three public registration endpoints (customer, merchant, courier).
 * There is deliberately no role or status field: the endpoint decides the role,
 * and unknown JSON properties such as "role" are ignored.
 */
public record RegisterRequest(
    @NotBlank
    @Size(max = 100)
    String name,

    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @NotBlank
    @Size(min = 8, max = 72)
    String password,

    // Optional. Digits with an optional leading +, spaces, dashes and brackets.
    @Size(max = 30)
    @Pattern(regexp = "^\\+?[0-9 ()-]*$")
    String phone,

    // Optional.
    @Size(max = 500)
    String address
) {
    public RegisterRequest(String name, String email, String password) {
        this(name, email, password, null, null);
    }
}
