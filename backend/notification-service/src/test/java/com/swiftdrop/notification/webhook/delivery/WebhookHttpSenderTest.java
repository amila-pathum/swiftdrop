package com.swiftdrop.notification.webhook.delivery;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.notification.webhook.security.WebhookSignature;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WebhookHttpSenderTest {

    private static final String SECRET = "test-webhook-secret";
    private static final Instant NOW =
            Instant.parse("2026-11-12T12:00:00Z");

    private static final byte[] BODY =
            "{\"eventId\":\"evt-1\",\"newStatus\":\"DELIVERED\"}"
                    .getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private WebhookHttpSender sender;
    private URI endpoint;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.start();

        endpoint = URI.create(
                "http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + "/webhook"
        );

        sender = new WebhookHttpSender(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOriginalBodyWithValidSignature() throws Exception {
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedEventId = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        AtomicBoolean validSignature = new AtomicBoolean();

        server.createContext("/webhook", exchange -> {
            byte[] received = exchange.getRequestBody().readAllBytes();

            receivedBody.set(received);
            receivedEventId.set(exchange.getRequestHeaders()
                    .getFirst("X-SwiftDrop-Event-Id"));
            receivedContentType.set(exchange.getRequestHeaders()
                    .getFirst("Content-Type"));

            long timestamp = Long.parseLong(
                    exchange.getRequestHeaders()
                            .getFirst("X-SwiftDrop-Timestamp")
            );

            validSignature.set(WebhookSignature.verify(
                    SECRET,
                    timestamp,
                    received,
                    exchange.getRequestHeaders()
                            .getFirst("X-SwiftDrop-Signature"),
                    NOW
            ));

            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        var result = sender.send(endpoint, SECRET, "evt-1", BODY);

        assertTrue(result.successful());
        assertEquals(Integer.valueOf(204), result.httpStatus());
        assertNull(result.failureCode());
        assertArrayEquals(BODY, receivedBody.get());
        assertEquals("evt-1", receivedEventId.get());
        assertEquals("application/json", receivedContentType.get());
        assertTrue(validSignature.get());
    }

    @Test
    void receiverFailureIsReturnedAsFailure() throws Exception {
        server.createContext("/webhook", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        var result = sender.send(endpoint, SECRET, "evt-1", BODY);

        assertFalse(result.successful());
        assertEquals(Integer.valueOf(500), result.httpStatus());
        assertEquals("HTTP_ERROR", result.failureCode());
    }

    @Test
    void redirectIsNotFollowed() throws Exception {
        AtomicInteger redirectedRequests = new AtomicInteger();

        server.createContext("/webhook", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/other");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/other", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        var result = sender.send(endpoint, SECRET, "evt-1", BODY);

        assertFalse(result.successful());
        assertEquals(Integer.valueOf(302), result.httpStatus());
        assertEquals(0, redirectedRequests.get());
    }

    @Test
    void wrongSecretIsRejectedByReceiver() throws Exception {
        server.createContext("/webhook", exchange -> {
            byte[] received = exchange.getRequestBody().readAllBytes();

            long timestamp = Long.parseLong(
                    exchange.getRequestHeaders()
                            .getFirst("X-SwiftDrop-Timestamp")
            );

            boolean valid = WebhookSignature.verify(
                    SECRET,
                    timestamp,
                    received,
                    exchange.getRequestHeaders()
                            .getFirst("X-SwiftDrop-Signature"),
                    NOW
            );

            exchange.sendResponseHeaders(valid ? 204 : 401, -1);
            exchange.close();
        });

        var result = sender.send(
                endpoint, "wrong-test-secret", "evt-1", BODY
        );

        assertFalse(result.successful());
        assertEquals(Integer.valueOf(401), result.httpStatus());
    }
}