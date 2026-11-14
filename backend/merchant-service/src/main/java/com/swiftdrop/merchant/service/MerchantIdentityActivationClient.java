package com.swiftdrop.merchant.service;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Uses the approving administrator's token; Identity remains the owner of account status. */
@Component
public class MerchantIdentityActivationClient {
    private final RestClient client;

    public MerchantIdentityActivationClient(
            @Value("${IDENTITY_SERVICE_URL:http://localhost:8081}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public void ensureActive(Long userId, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrator authentication is required");
        }
        if (userId == null || userId <= 0) throw conflict();
        try {
            var lookup = client.get().uri("/api/admin/users/{id}", userId)
                    .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().toEntity(Account.class);
            if (!lookup.getStatusCode().is2xxSuccessful()) throw unavailable();
            Account account = lookup.getBody();
            validateOwner(account, userId);
            if ("ACTIVE".equals(account.status())) return;
            if (!"PENDING_APPROVAL".equals(account.status())) throw conflict();
            var activation = client.post().uri("/api/admin/users/{id}/activate", userId)
                    .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().toEntity(Account.class);
            if (!activation.getStatusCode().is2xxSuccessful()) throw unavailable();
            Account activated = activation.getBody();
            validateOwner(activated, userId);
            if (!"ACTIVE".equals(activated.status())) throw unavailable();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator authorization could not be verified by Identity Service");
            }
            if (status == 404) throw new ResponseStatusException(HttpStatus.CONFLICT, "Linked login account not found");
            throw unavailable();
        } catch (RestClientException exception) {
            // Never expose remote bodies, headers or exception text containing credentials.
            throw unavailable();
        }
    }

    private static void validateOwner(Account account, Long userId) {
        if (account == null || !userId.equals(account.id()) || !"MERCHANT".equals(account.role())) throw conflict();
    }
    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Linked account must be a pending or active merchant; suspended and disabled accounts cannot be activated here");
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account activation could not be confirmed. Retry approval to check the account status");
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(Long id, String role, String status) { }
}
