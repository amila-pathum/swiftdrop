package com.swiftdrop.merchant.merchantapi.client;

import java.net.http.HttpClient;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class IdentityMerchantTokenClient {
    private final RestClient client;
    private final String serviceToken;
    private final JwtDecoder decoder;
    public IdentityMerchantTokenClient(@Value("${IDENTITY_SERVICE_URL:http://localhost:8081}") String url,
            @Value("${merchant.order.service-token:${MERCHANT_ORDER_SERVICE_TOKEN:}}") String serviceToken, JwtDecoder decoder) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.serviceToken = serviceToken; this.decoder = decoder;
    }
    public String forUser(long userId) {
        if (serviceToken.length() < 32) throw unavailable();
        try {
            var reply = client.post().uri("/internal/merchant-order-tokens")
                    .header("X-SwiftDrop-Merchant-Token", serviceToken).body(Map.of("userId", userId))
                    .retrieve().toEntity(Token.class);
            var body = reply.getBody();
            if (!reply.getStatusCode().is2xxSuccessful() || body == null || !Long.valueOf(userId).equals(body.userId())
                    || body.accessToken() == null || body.expiresIn() == null || body.expiresIn() > 60 || body.expiresIn() <= 0) throw unavailable();
            var jwt = decoder.decode(body.accessToken());
            if (!Long.toString(userId).equals(jwt.getSubject()) || !"MERCHANT".equals(jwt.getClaimAsString("role"))
                    || !"ACTIVE".equals(jwt.getClaimAsString("status"))
                    || !"MERCHANT_ORDER_INTEGRATION".equals(jwt.getClaimAsString("purpose"))
                    || !jwt.getAudience().containsAll(java.util.List.of("parcel-service", "pricing-service"))
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())
                    || jwt.getExpiresAt().isAfter(Instant.now().plusSeconds(65))) throw unavailable();
            return body.accessToken();
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Linked merchant login must be active");
        } catch (ResponseStatusException exception) { throw exception; }
        catch (RuntimeException exception) { throw unavailable(); }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Merchant delegation is unavailable");
    }
    private record Token(Long userId, String accessToken, Integer expiresIn) {
        @Override public String toString() { return "Token[userId=" + userId + "]"; }
    }
}
