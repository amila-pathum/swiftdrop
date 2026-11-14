package com.swiftdrop.merchant.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class MerchantIdentityActivationClientTest {
    HttpServer server;
    MerchantIdentityActivationClient client;
    final List<String> requests = new ArrayList<>();
    final List<String> tokens = new ArrayList<>();
    String state = "PENDING_APPROVAL", role = "MERCHANT";
    long responseId = 20;
    int lookupStatus = 200, activateStatus = 200;

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            tokens.add(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean post = exchange.getRequestMethod().equals("POST");
            if (post) state = "ACTIVE";
            byte[] body = ("{\"id\":" + responseId + ",\"role\":\"" + role + "\",\"status\":\"" + state
                    + "\",\"name\":\"Extra identity response field\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(post ? activateStatus : lookupStatus, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        client = new MerchantIdentityActivationClient("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void cleanup() { server.stop(0); }
    void activate() { client.ensureActive(20L, "Bearer administrator-token"); }
    int failure() { return assertThrows(ResponseStatusException.class, this::activate).getStatusCode().value(); }

    @Test void pendingMerchantUsesLinkedUserIdAndForwardsTheAdminsToken() {
        activate();
        assertEquals(List.of("GET /api/admin/users/20", "POST /api/admin/users/20/activate"), requests);
        assertEquals(List.of("Bearer administrator-token", "Bearer administrator-token"), tokens);
    }
    @Test void alreadyActiveAccountIsNotActivatedAgain() {
        state = "ACTIVE"; activate(); assertEquals(1, requests.size());
    }
    @Test void suspendedDisabledAndNonMerchantAccountsCannotBeActivated() {
        for (String blocked : List.of("SUSPENDED", "DISABLED")) { state = blocked; assertEquals(409, failure()); }
        state = "PENDING_APPROVAL"; role = "CUSTOMER"; assertEquals(409, failure());
        role = "MERCHANT"; responseId = 999; assertEquals(409, failure());
        assertTrue(requests.stream().allMatch(request -> request.startsWith("GET")));
    }
    @Test void retryRecoversAnUncertainActivationUsingTheCurrentIdentityState() {
        activateStatus = 503;
        assertEquals(503, failure());
        activate();
        assertEquals(1, requests.stream().filter(request -> request.startsWith("POST")).count());
    }
    @Test void authorizationFailureIsNotReportedAsApproval() {
        lookupStatus = 403; assertEquals(403, failure()); assertEquals(1, requests.size());
    }
    @Test void redirectResponseIsNotTreatedAsAnActiveAccount() {
        state = "ACTIVE"; lookupStatus = 302; assertEquals(503, failure());
    }
    @Test void missingCredentialsFailBeforeAnyRemoteRequest() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> client.ensureActive(20L, null)).getStatusCode().value());
        assertTrue(requests.isEmpty());
    }
}
