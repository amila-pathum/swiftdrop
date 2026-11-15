package com.swiftdrop.notification.webhook.retry;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpMerchantWebhookSettingsLookupTest {
    private HttpServer server;
    private HttpMerchantWebhookSettingsLookup lookup;
    private int status;
    private String body;
    private String receivedToken;
    @BeforeEach void start() throws Exception {
        status = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/merchants/1/webhook", exchange -> {
            receivedToken = exchange.getRequestHeaders().getFirst("X-SwiftDrop-Internal-Token");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        lookup = new HttpMerchantWebhookSettingsLookup("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-internal-token-at-least-32-characters");
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void enabledSettingsUseAuthenticatedLookup() {
        body = "{\"enabled\":true,\"url\":\"https://example.test/hook\",\"signingSecret\":\"test-secret\"}";
        var settings = lookup.findForMerchant("1").orElseThrow();
        assertEquals("test-secret", settings.signingSecret());
        assertEquals("test-internal-token-at-least-32-characters", receivedToken);
    }
    @Test void disabledSettingsAreAbsent() {
        body = "{\"enabled\":false}";
        assertTrue(lookup.findForMerchant("1").isEmpty());
    }
    @Test void explicitMissingMerchantIsAbsent() {
        status = 404; body = "{\"error\":\"MERCHANT_NOT_FOUND\"}";
        assertTrue(lookup.findForMerchant("1").isEmpty());
    }
    @Test void routingNotFoundIsNotTreatedAsDisabled() {
        status = 404; body = "{\"error\":\"Not Found\"}";
        assertThrows(RuntimeException.class, () -> lookup.findForMerchant("1"));
    }
    @Test void authenticationAndOutageFailuresAreNotTreatedAsDisabled() {
        body = "{}";
        for (int code : new int[]{401, 403, 500, 503, 302}) {
            status = code;
            assertThrows(RuntimeException.class, () -> lookup.findForMerchant("1"));
        }
    }
    @Test void malformedSuccessIsNotTreatedAsDisabled() {
        for (String json : new String[]{"{}", "{\"enabled\":true}", "null"}) {
            body = json;
            assertThrows(RuntimeException.class, () -> lookup.findForMerchant("1"));
        }
    }
}
