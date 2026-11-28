package com.swiftdrop.notification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Looks up users in Identity Service through its internal API (/internal/users),
 * authenticated with the shared service token. Never forges user tokens.
 */
@Slf4j
@Component
public class IdentityServiceClient {

    public static final String TOKEN_HEADER = "X-SwiftDrop-Internal-Token";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String identityServiceUrl;
    private final String serviceToken;

    @Autowired
    public IdentityServiceClient(
            ObjectMapper objectMapper,
            @Value("${notification.identity.url:http://localhost:8081}") String identityServiceUrl,
            @Value("${swiftdrop.internal.service-token:}") String serviceToken) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                objectMapper, identityServiceUrl, serviceToken);
    }

    public IdentityServiceClient(HttpClient httpClient, ObjectMapper objectMapper,
            String identityServiceUrl, String serviceToken) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.identityServiceUrl = identityServiceUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    /** The registered email of an identity user id (or the value itself when it already is an email). */
    public Optional<String> findEmailByCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Optional.empty();
        }
        if (customerId.contains("@")) {
            return Optional.of(customerId.trim());
        }
        long userId;
        try {
            userId = Long.parseLong(customerId.trim());
        } catch (NumberFormatException notNumeric) {
            log.debug("CustomerId '{}' is not numeric, skipping identity lookup", customerId);
            return Optional.empty();
        }
        try {
            HttpResponse<String> response = get(URI.create(identityServiceUrl + "/internal/users/" + userId));
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonNode email = objectMapper.readTree(response.body()).get("email");
                if (email != null && !email.asText().isBlank()) {
                    return Optional.of(email.asText().trim());
                }
            } else {
                log.warn("Identity service returned HTTP {} looking up user {}", response.statusCode(), userId);
            }
        } catch (Exception ex) {
            log.warn("Could not look up user {} in identity service at {}: {}", userId, identityServiceUrl, ex.getMessage());
        }
        return Optional.empty();
    }

    /** Identity user ids of every ACTIVE account with the role. Empty when identity service cannot be reached. */
    public List<String> findActiveUserIdsByRole(String role) {
        List<String> ids = new ArrayList<>();
        try {
            URI uri = URI.create(identityServiceUrl + "/internal/users?role="
                    + URLEncoder.encode(role, StandardCharsets.UTF_8) + "&status=ACTIVE");
            HttpResponse<String> response = get(uri);
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                log.warn("Identity service returned HTTP {} listing {} users", response.statusCode(), role);
                return ids;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                root.forEach(id -> ids.add(id.asText()));
            }
        } catch (Exception ex) {
            log.warn("Could not list {} users from identity service at {}: {}", role, identityServiceUrl, ex.getMessage());
        }
        return ids;
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        if (serviceToken.length() < 32) {
            throw new IllegalStateException("swiftdrop.internal.service-token (INTERNAL_WEBHOOK_TOKEN) is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .header(TOKEN_HEADER, serviceToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
