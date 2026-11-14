package com.swiftdrop.merchant.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class HttpMerchantParcelClient implements MerchantParcelClient {

    private static final Logger log = LoggerFactory.getLogger(HttpMerchantParcelClient.class);

    private final RestClient restClient;

    public HttpMerchantParcelClient(
            org.springframework.beans.factory.ObjectProvider<RestClient.Builder> builderProvider,
            @Value("${swiftdrop.parcel.base-url:http://localhost:8082}") String parcelBaseUrl
    ) {
        RestClient.Builder builder = builderProvider.getIfAvailable(RestClient::builder);
        this.restClient = builder.clone()
                .baseUrl(parcelBaseUrl)
                .requestInterceptor((request, body, execution) -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication instanceof JwtAuthenticationToken jwt) {
                        request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public List<MerchantParcelDto> getParcelsByMerchant(Long userId, Long merchantId) {
        try {
            Map<String, Object> pageResponse = restClient.get()
                    .uri("/api/parcels/my?page=0&size=50")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (pageResponse != null && pageResponse.containsKey("content")) {
                Object content = pageResponse.get("content");
                if (content instanceof List<?> rawList) {
                    return parseParcelList(rawList);
                }
            }
            return Collections.emptyList();
        } catch (RestClientException ex) {
            log.debug("Parcel service returned error or is unavailable: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Failed to retrieve merchant parcels: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<MerchantParcelHistoryDto> getParcelHistory(String parcelId) {
        if (parcelId == null || parcelId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/parcels/{id}/history", parcelId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            List<MerchantParcelHistoryDto> list = new ArrayList<>();
            for (Map<String, Object> map : response) {
                String id = map.get("id") != null ? map.get("id").toString() : null;
                String prev = (String) map.get("previousStatus");
                String next = (String) map.get("newStatus");
                String note = (String) map.get("note");
                Instant changedAt = null;
                if (map.get("changedAt") != null) {
                    try {
                        changedAt = Instant.parse(map.get("changedAt").toString());
                    } catch (Exception ignored) {
                    }
                }
                list.add(new MerchantParcelHistoryDto(id, prev, next, changedAt, note));
            }
            return list;
        } catch (RestClientException ex) {
            log.debug("Unable to fetch parcel history: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MerchantParcelDto> parseParcelList(List<?> rawList) {
        List<MerchantParcelDto> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String id = map.get("id") != null ? map.get("id").toString() : null;
            String trackingNumber = (String) map.get("trackingNumber");
            Long senderId = null;
            if (map.get("senderId") instanceof Number n) {
                senderId = n.longValue();
            }
            String receiverName = (String) map.get("receiverName");
            String receiverPhone = (String) map.get("receiverPhone");
            String pickupAddress = (String) map.get("pickupAddress");
            String deliveryAddress = (String) map.get("deliveryAddress");
            String status = (String) map.get("status");
            String mId = map.get("merchantId") != null ? map.get("merchantId").toString() : null;
            String ref = (String) map.get("merchantOrderReference");
            Instant createdAt = null;
            if (map.get("createdAt") != null) {
                try {
                    createdAt = Instant.parse(map.get("createdAt").toString());
                } catch (Exception ignored) {
                }
            }

            result.add(new MerchantParcelDto(
                    id, trackingNumber, senderId, receiverName, receiverPhone,
                    pickupAddress, deliveryAddress, status, mId, ref, createdAt
            ));
        }
        return result;
    }
}