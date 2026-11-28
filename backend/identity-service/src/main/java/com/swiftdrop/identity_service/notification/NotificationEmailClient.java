package com.swiftdrop.identity_service.notification;

import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.internal.InternalServiceSecurity;
import com.swiftdrop.identity_service.service.PasswordResetDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asks Notification Service to email a user (welcome, password reset). Sends
 * after the surrounding transaction commits and in the background, so a slow
 * or unavailable mail server never blocks or rolls back sign-up or a reset request.
 *
 * <p>Registered only when the shared service token is configured; without it,
 * password reset by email stays unavailable (501) instead of pretending to send.
 */
@Component
@ConditionalOnExpression("'${swiftdrop.internal.service-token:}'.length() >= 32")
public class NotificationEmailClient implements PasswordResetDelivery {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailClient.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String token;
    private final String frontendOrigin;

    public NotificationEmailClient(ObjectMapper mapper,
            @Value("${swiftdrop.notification-service-url:http://localhost:8087}") String baseUrl,
            @Value("${swiftdrop.internal.service-token}") String token,
            @Value("${swiftdrop.frontend-origin}") String frontendOrigin) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    @Override
    public void send(User user, String resetUrl, Instant expiresAt) {
        post("/internal/emails/password-reset", Map.of(
            "email", user.getEmail(), "name", user.getName(),
            "resetUrl", resetUrl, "expiresAt", expiresAt.toString()), "password reset", user.getId());
    }

    /** Welcome for a new account; merchants and couriers are told their application is under review. */
    public void sendWelcome(User user) {
        post("/internal/emails/welcome", Map.of(
            "email", user.getEmail(), "name", user.getName(), "role", user.getRole().name(),
            "status", user.getStatus().name(), "signInUrl", frontendOrigin + "/login"), "welcome", user.getId());
    }

    private void post(String path, Map<String, String> body, String kind, Long userId) {
        Runnable task = () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header(InternalServiceSecurity.HEADER, token)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() / 100 == 2) {
                    log.info("Requested {} email for user {}", kind, userId);
                } else {
                    log.warn("Notification service answered HTTP {} to the {} email for user {}",
                        response.statusCode(), kind, userId);
                }
            } catch (Exception exception) {
                log.warn("Could not request the {} email for user {}: {}", kind, userId, exception.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            executor.execute(task);
        }
    }
}
