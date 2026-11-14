package com.swiftdrop.merchant.merchantapi.controller;

import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

/** Keeps the browser on the existing merchant gateway route and Notification's database private. */
@RestController
@RequestMapping("/api/merchants/me/webhook/deliveries")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantDeliveryController {
    private final MerchantRepository merchants;
    private final RestClient client;
    private final String token;
    public MerchantDeliveryController(MerchantRepository merchants,
            @Value("${swiftdrop.notification-service-url:${NOTIFICATION_SERVICE_URL:http://localhost:8087}}") String url,
            @Value("${swiftdrop.internal.webhook-token:}") String token) {
        this.merchants = merchants; this.token = token;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @GetMapping
    public ResponseEntity<String> history(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="false") boolean failedOnly,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return call(jwt, HttpMethod.GET, "?failedOnly=" + failedOnly + "&page=" + page + "&size=" + size);
    }
    @GetMapping("/{id}")
    public ResponseEntity<String> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return call(jwt, HttpMethod.GET, "/" + id);
    }
    @PostMapping("/{id}/retry")
    public ResponseEntity<String> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return call(jwt, HttpMethod.POST, "/" + id + "/retry");
    }
    @PostMapping("/test")
    public ResponseEntity<String> test(@AuthenticationPrincipal Jwt jwt) {
        return call(jwt, HttpMethod.POST, "/test");
    }
    private ResponseEntity<String> call(Jwt jwt, HttpMethod method, String suffix) {
        long userId;
        try { userId = Long.parseLong(jwt.getSubject()); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identity"); }
        var merchant = merchants.findByUserId(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found"));
        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant must be approved");
        }
        if (token.length() < 32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook management unavailable");
        try {
            var result = client.method(method).uri("/internal/webhooks/merchants/" + merchant.getId()
                    + "/deliveries" + suffix).header("X-SwiftDrop-Internal-Token", token)
                    .retrieve().toEntity(String.class);
            if (!result.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Unexpected response");
            return ResponseEntity.status(result.getStatusCode()).cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON).body(result.getBody());
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 409 || status == 400) {
                throw new ResponseStatusException(HttpStatus.valueOf(status),
                        status == 404 ? "Delivery not found" : "Webhook action is not available in the current state");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook management unavailable");
        } catch (RestClientException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook management unavailable");
        }
    }
}
