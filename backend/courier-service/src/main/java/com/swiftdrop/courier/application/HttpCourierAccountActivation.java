package com.swiftdrop.courier.application;

import com.swiftdrop.courier.client.CourierIntegrations.RemoteFailure;
import com.swiftdrop.courier.exception.CourierException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpCourierAccountActivation implements CourierAccountActivation {
    private static final String SERVICE = "Identity service";
    private final RestClient client;

    public HttpCourierAccountActivation(
            @Value("${swiftdrop.clients.identity-url:http://localhost:8081}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public void ensureActive(Long userId, String adminToken) {
        try {
            Account account = client.get().uri("/api/admin/users/{id}", userId)
                .headers(h -> h.setBearerAuth(adminToken)).retrieve().body(Account.class);
            requireCourier(account, userId);
            if ("ACTIVE".equals(account.status())) return;
            if (!"PENDING_APPROVAL".equals(account.status())) throw notApprovable();
            Account activated = client.post().uri("/api/admin/users/{id}/activate", userId)
                .headers(h -> h.setBearerAuth(adminToken)).retrieve().body(Account.class);
            requireCourier(activated, userId);
            if (!"ACTIVE".equals(activated.status())) throw new RemoteFailure(SERVICE, false);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw CourierException.forbidden(
                    "Administrator authorization could not be verified by the Identity service.");
            }
            if (status == 404) throw CourierException.conflict("The applicant's login account was not found.");
            // Never pass on remote bodies or exception text.
            throw new RemoteFailure(SERVICE, false);
        } catch (RestClientException e) {
            throw new RemoteFailure(SERVICE, false);
        }
    }

    private static void requireCourier(Account account, Long userId) {
        if (account == null || !userId.equals(account.id()) || !"COURIER".equals(account.role())) {
            throw notApprovable();
        }
    }

    private static CourierException notApprovable() {
        return CourierException.conflict(
            "The login account must be a pending or active courier; suspended and disabled accounts cannot be approved here.");
    }

    record Account(Long id, String role, String status) {}
}
