package com.swiftdrop.notification.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class IdentityServiceClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String identityServiceUrl;
    private final String jwtSecret;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityServiceClient(
            ObjectMapper objectMapper,
            @Value("${notification.identity.url:http://localhost:8081}") String identityServiceUrl,
            @Value("${swiftdrop.jwt.secret:}") String jwtSecret) {
        this.objectMapper = objectMapper;
        this.identityServiceUrl = identityServiceUrl;
        this.jwtSecret = jwtSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /** Constructor for testing with mock HttpClient. */
    public IdentityServiceClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String identityServiceUrl,
            String jwtSecret) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.identityServiceUrl = identityServiceUrl;
        this.jwtSecret = jwtSecret;
    }

    /**
     * Queries identity-service to resolve the user's email address by customerId.
     */
    public Optional<String> findEmailByCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Optional.empty();
        }

        // 1. If customerId is already an email address
        if (customerId.contains("@")) {
            return Optional.of(customerId.trim());
        }

        // 2. Query identity-service if customerId is a numeric ID
        try {
            long userId = Long.parseLong(customerId.trim());
            String token = generateInternalAdminToken();

            URI uri = URI.create(identityServiceUrl + "/api/admin/users/" + userId);
            log.debug("Calling identity-service to fetch customer details: url={}", uri);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(3))
                    .GET();

            if (!token.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode emailNode = root.get("email");
                if (emailNode != null && !emailNode.asText().isBlank()) {
                    return Optional.of(emailNode.asText().trim());
                }
            }
        } catch (NumberFormatException nfe) {
            log.debug("CustomerId '{}' is not numeric, skipping identity-service query", customerId);
        } catch (Exception ex) {
            log.warn("Could not retrieve customer email from identity-service at {} for customerId '{}': {}",
                    identityServiceUrl, customerId, ex.getMessage());
        }

        return Optional.empty();
    }

    private String generateInternalAdminToken() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return "";
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            return Jwts.builder()
                    .subject("notification-service")
                    .claim("email", "internal-notification@swiftdrop.com")
                    .claim("role", "ADMIN")
                    .claim("status", "ACTIVE")
                    .issuedAt(new Date())
                    .expiration(Date.from(Instant.now().plusSeconds(300)))
                    .signWith(Keys.hmacShaKeyFor(keyBytes))
                    .compact();
        } catch (Exception ex) {
            log.warn("Failed to generate internal admin token: {}", ex.getMessage());
            return "";
        }
    }
}
