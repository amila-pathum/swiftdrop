package com.swiftdrop.identity_service.security;

import com.swiftdrop.identity_service.entity.Role;

public record AuthenticatedUser(long id, String email, Role role) {}
