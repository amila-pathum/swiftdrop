package com.swiftdrop.identity_service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityServiceApplicationTests {

    @Test
    void applicationEntryPointIsAvailable() {
        assertThat(IdentityServiceApplication.class).isNotNull();
    }
}
