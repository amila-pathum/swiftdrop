package com.swiftdrop.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAccessRulesTest {

    @Test
    void publicTrackingIsOpenForGetOnly() {
        assertThat(GatewayAccessRules.isPublic(HttpMethod.GET, "/api/parcels/track/SD123")).isTrue();
        assertThat(GatewayAccessRules.isPublic(HttpMethod.POST, "/api/parcels/track/SD123")).isFalse();
        assertThat(GatewayAccessRules.isPublic(HttpMethod.GET, "/api/parcels/track/SD123/extra")).isFalse();
    }

    @Test
    void logoutAndProfileNeedAToken() {
        assertThat(GatewayAccessRules.isPublic(HttpMethod.POST, "/api/auth/logout")).isFalse();
        assertThat(GatewayAccessRules.isPublic(HttpMethod.GET, "/api/users/me")).isFalse();
        assertThat(GatewayAccessRules.isPublic(HttpMethod.GET, "/api/auth/login")).isFalse();
    }

    @Test
    void merchantApiIsOpenBecauseItUsesApiKeys() {
        assertThat(GatewayAccessRules.isPublic(HttpMethod.POST, "/api/merchants/integration/orders")).isTrue();
        assertThat(GatewayAccessRules.isPublic(HttpMethod.GET, "/api/merchants/me")).isFalse();
    }

    @Test
    void queryTokenOnlyOnTheUserStream() {
        assertThat(GatewayAccessRules.allowsQueryToken(HttpMethod.GET, "/api/notifications/stream")).isTrue();
        assertThat(GatewayAccessRules.allowsQueryToken(HttpMethod.GET, "/api/notifications")).isFalse();
    }

    @Test
    void adminRoutesNeedAdmin() {
        assertThat(GatewayAccessRules.requiredRole(HttpMethod.GET, "/api/admin/users")).contains("ADMIN");
        assertThat(GatewayAccessRules.requiredRole(HttpMethod.GET, "/api/parcels/my")).isEmpty();
    }
}
