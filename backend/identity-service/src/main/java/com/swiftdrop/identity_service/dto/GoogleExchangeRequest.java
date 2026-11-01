package com.swiftdrop.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The one-time code from the Google callback, and the PKCE verifier (43-128 chars) it is bound to. */
public record GoogleExchangeRequest(
    @NotBlank @Size(max = 100) String code,
    @NotBlank @Size(min = 43, max = 128) String verifier
) {}
