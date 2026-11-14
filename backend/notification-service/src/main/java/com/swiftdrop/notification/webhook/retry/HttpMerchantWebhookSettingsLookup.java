package com.swiftdrop.notification.webhook.retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

@Component
public class HttpMerchantWebhookSettingsLookup
        implements MerchantWebhookSettingsLookup {

    private final RestClient client;
    private final String token;

    public HttpMerchantWebhookSettingsLookup(
            @Value("${swiftdrop.merchant-service-url:http://localhost:8083}")
            String baseUrl,
            @Value("${swiftdrop.internal.webhook-token:}")
            String token
    ) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        this.token = token;
    }

    @Override
    public Optional<Settings> findForMerchant(String merchantId) {
        if (token.length() < 32) {
            throw new IllegalStateException(
                    "Internal webhook token must be configured"
            );
        }

        RestClient.RequestHeadersSpec<?> request = client.get()
                .uri("/internal/merchants/{id}/webhook", merchantId);

        request.header("X-SwiftDrop-Internal-Token", token);

        SettingsResponse result;
        try {
            result = request.retrieve().body(SettingsResponse.class);
        } catch (HttpClientErrorException.NotFound exception) {
            // Only the owning controller's explicit absence result is terminal.
            // A proxy or route 404 remains a dependency failure, eligible for retry.
            ErrorResponse error = exception.getResponseBodyAs(ErrorResponse.class);
            if (error != null && "MERCHANT_NOT_FOUND".equals(error.error())) {
                return Optional.empty();
            }
            throw exception;
        }

        if (result == null) {
            throw new IllegalStateException(
                    "Merchant settings response was empty"
            );
        }

        if (result.enabled() == null) {
            throw new IllegalStateException("Merchant settings response is incomplete");
        }
        if (!result.enabled()) {
            return Optional.empty();
        }

        if (result.url() == null || result.signingSecret() == null
                || result.signingSecret().isBlank()) {
            throw new IllegalStateException(
                    "Enabled webhook settings are incomplete"
            );
        }

        return Optional.of(new Settings(
                URI.create(result.url()),
                result.signingSecret(),
                true
        ));
    }

    private record ErrorResponse(String error) { }

    private record SettingsResponse(
            String url,
            String signingSecret,
            Boolean enabled
    ) {
        @Override
        public String toString() {
            return "SettingsResponse[enabled=" + enabled + "]";
        }
    }
}
