package com.swiftdrop.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayIntegrationTest {
    private static final AtomicReference<String> path = new AtomicReference<>();
    private static final AtomicReference<String> method = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<String> cookie = new AtomicReference<>();
    private static DisposableServer upstream;

    @Autowired
    WebTestClient client;

    @BeforeAll
    static void startUpstream() {
        upstream = HttpServer.create().port(0).handle((request, response) -> {
            path.set(request.uri());
            method.set(request.method().name());
            authorization.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
            cookie.set(request.requestHeaders().get(HttpHeaders.COOKIE));
            response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.header(HttpHeaders.CONNECTION, "close");

            if (request.uri().startsWith("/oauth2/authorization/google")) {
                return response.status(302)
                    .header(HttpHeaders.LOCATION, "https://accounts.google.com/o/oauth2/v2/auth?state=s")
                    .header(HttpHeaders.SET_COOKIE, "JSESSIONID=oauth-session; Path=/; HttpOnly; SameSite=Lax")
                    .send();
            }
            if (request.uri().startsWith("/login/oauth2/code/google")) {
                return response.status(302)
                    .header(HttpHeaders.LOCATION, "http://localhost:3000/auth/google/callback#code=abc")
                    .send();
            }
            if (request.uri().equals("/api/users/me")) {
                return response.status(401).sendString(
                    Mono.just("{\"error\":\"Authentication is required\"}"));
            }
            if ("conflict".equals(request.requestHeaders().get("X-Test-Response"))) {
                return response.status(409).sendString(
                    Mono.just("{\"error\":\"Email is already registered\"}"));
            }
            int status = request.uri().endsWith("register") ? 201 : 200;
            return response.status(status).sendString(Mono.just("{\"proxied\":true}"));
        }).bindNow();
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.disposeNow();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_SERVICE_URL", () -> "http://localhost:" + upstream.port());
        for (String service : new String[] {"PARCEL", "MERCHANT", "COURIER", "DISPATCH", "PRICING"}) {
            registry.add(service + "_SERVICE_URL", () -> "http://localhost:" + upstream.port());
        }
        registry.add("FRONTEND_ORIGIN", () -> "http://localhost:3000");
    }

    @Test
    @Order(1)
    void contextStartsAndHealthIsAvailable() {
        client.get().uri("/actuator/health").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @Order(2)
    void registrationPreservesPathMethodAndResponse() {
        client.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isCreated()
            .expectBody().jsonPath("$.proxied").isEqualTo(true);
        assertThat(path.get()).isEqualTo("/api/auth/register");
        assertThat(method.get()).isEqualTo("POST");
    }

    @Test
    @Order(3)
    void loginPreservesPathAndMethod() {
        client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isOk();
        assertThat(path.get()).isEqualTo("/api/auth/login");
        assertThat(method.get()).isEqualTo("POST");
    }

    @Test
    @Order(4)
    void authorizationHeaderAndUnauthorizedResponseArePreserved() {
        client.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer test-token").exchange()
            .expectStatus().isUnauthorized()
            .expectBody().jsonPath("$.error").isEqualTo("Authentication is required");
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
    }

    @Test
    @Order(5)
    void upstreamConflictIsPreserved() {
        client.post().uri("/api/auth/register")
            .header("X-Test-Response", "conflict")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody().jsonPath("$.error").isEqualTo("Email is already registered");
    }

    @Test
    @Order(6)
    void corsPreflightReturnsConfiguredHeaders() {
        client.options().uri("/api/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                "http://localhost:3000")
            .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                value -> assertThat(value).contains("POST"))
            .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                value -> assertThat(value.toLowerCase()).contains("authorization"));
    }

    @Test
    @Order(7)
    void googleSignInStartPassesRedirectAndSessionCookieThrough() {
        client.get().uri("/oauth2/authorization/google?client_challenge=abc_-123").exchange()
            .expectStatus().isFound()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "https://accounts.google.com/o/oauth2/v2/auth?state=s")
            .expectHeader().value(HttpHeaders.SET_COOKIE,
                value -> assertThat(value).startsWith("JSESSIONID=oauth-session"));
        assertThat(path.get()).isEqualTo("/oauth2/authorization/google?client_challenge=abc_-123");
    }

    @Test
    @Order(8)
    void googleCallbackPassesQueryCookieAndRedirectThrough() {
        client.get().uri("/login/oauth2/code/google?state=s&code=google-code")
            .header(HttpHeaders.COOKIE, "JSESSIONID=oauth-session").exchange()
            .expectStatus().isFound()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "http://localhost:3000/auth/google/callback#code=abc");
        assertThat(path.get()).isEqualTo("/login/oauth2/code/google?state=s&code=google-code");
        assertThat(cookie.get()).isEqualTo("JSESSIONID=oauth-session");
    }

    @Test
    @Order(9)
    void googleCodeExchangePreservesPathAndMethod() {
        client.post().uri("/api/auth/google/exchange")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isOk();
        assertThat(path.get()).isEqualTo("/api/auth/google/exchange");
        assertThat(method.get()).isEqualTo("POST");
    }

    @Test
    @Order(10)
    void otherOAuthPathsAreNotRouted() {
        path.set(null);
        client.get().uri("/oauth2/authorization/github").exchange()
            .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(400));
        assertThat(path.get()).isNull();
    }

    @ParameterizedTest
    @Order(11)
    @ValueSource(strings = {
        "/api/auth/register/merchant",
        "/api/auth/register/courier",
        "/api/auth/logout",
        "/api/auth/password-reset/request",
        "/api/auth/password-reset/confirm",
        "/api/users/me/change-password",
        "/api/admin/users",
        "/api/admin/users/42/status",
        "/api/admin/users/42/password-reset",
        "/api/parcels",
        "/api/parcels/SD-1001/tracking",
        "/api/merchants/42/webhooks",
        "/api/couriers/7/availability",
        "/api/dispatch/zones",
        "/api/pricing/quotes"
    })
    void serviceRoutesPreservePathAndMethod(String requestPath) {
        client.post().uri(requestPath)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isOk();
        assertThat(path.get()).isEqualTo(requestPath);
        assertThat(method.get()).isEqualTo("POST");
    }

    @Test
    @Order(12)
    void notificationServiceIsNotExposed() {
        path.set(null);
        client.get().uri("/api/notifications/preferences").exchange()
            .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(400));
        assertThat(path.get()).isNull();
    }

    @Test
    @Order(13)
    void unavailableUpstreamReturnsSafeBadGateway() {
        upstream.disposeNow();
        client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
            .expectBody().jsonPath("$.error").isEqualTo("Upstream service is unavailable");
    }
}
