package com.swiftdrop.merchant.merchantapi.internal;

import com.swiftdrop.merchant.service.MerchantWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real servlet requests exercise error dispatch, which MockMvc does not perform. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "swiftdrop.internal.webhook-token=internal-test-token-with-at-least-32-characters"
})
@Testcontainers(disabledWithoutDocker = true)
class InternalWebhookHttpIntegrationTest {
    private static final String TOKEN = "internal-test-token-with-at-least-32-characters";
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Value("${local.server.port}") int port;
    @MockitoBean MerchantWebhookService service;

    private HttpResponse<String> get(String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/internal/merchants/1/webhook"));
        if (token != null) request.header("X-SwiftDrop-Internal-Token", token);
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void missingAndInvalidTokensCannotReachService() throws Exception {
        assertEquals(401, get(null).statusCode());
        assertEquals(401, get("invalid").statusCode());
        verifyNoInteractions(service);
    }

    @Test void approvedSettingsReachAuthenticatedCallerWithoutCaching() throws Exception {
        when(service.getInternalSettings(1L)).thenReturn(
                new MerchantWebhookService.InternalWebhookSettings("https://example.test/hook", "test-secret", true));
        var response = get(TOKEN);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("test-secret"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test void disabledSettingsRemainDisabled() throws Exception {
        when(service.getInternalSettings(1L)).thenReturn(
                new MerchantWebhookService.InternalWebhookSettings(null, null, false));
        var response = get(TOKEN);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"enabled\":false"));
    }

    @Test void missingMerchantIsNotDisguisedAsAuthenticationFailure() throws Exception {
        when(service.getInternalSettings(1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404, get(TOKEN).statusCode());
    }

    @Test void settingsFailureIsNotDisguisedAsAuthenticationFailure() throws Exception {
        when(service.getInternalSettings(1L)).thenThrow(new IllegalStateException("test-only failure"));
        var response = get(TOKEN);
        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("test-only failure"));
    }
}
