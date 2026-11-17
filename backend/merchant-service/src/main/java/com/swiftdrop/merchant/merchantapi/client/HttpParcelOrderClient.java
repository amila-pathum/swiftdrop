package com.swiftdrop.merchant.merchantapi.client;

import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.merchantapi.dto.*;
import com.swiftdrop.merchant.merchantapi.service.MerchantOrderStore;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpParcelOrderClient implements ParcelOrderClient {
    private static final String PATH = "/api/parcels/merchant/integration/orders";
    private final RestClient client;
    private final MerchantRepository merchants;
    private final IdentityMerchantTokenClient tokens;
    private final ObjectMapper mapper;
    public HttpParcelOrderClient(@Value("${PARCEL_SERVICE_URL:http://localhost:8082}") String url,
            MerchantRepository merchants, IdentityMerchantTokenClient tokens, ObjectMapper mapper) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(12));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.merchants = merchants; this.tokens = tokens; this.mapper = mapper;
    }
    private String token(String profileId) {
        long id;
        try { id = Long.parseLong(profileId); if (id <= 0 || !Long.toString(id).equals(profileId)) throw new NumberFormatException(); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        var merchant = merchants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        if (merchant.getStatus() != MerchantStatus.APPROVED) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant must be approved");
        return tokens.forUser(merchant.getUserId());
    }
    @Override public boolean idempotentCreation() { return true; }
    @Override public MerchantOrderResponse createOrder(String merchantId, CreateMerchantOrderRequest order) {
        String bearer = token(merchantId);
        var parcel = order.parcel();
        String size = parcel.size() == null ? "MEDIUM" : parcel.size();
        String service = parcel.serviceType() == null ? "STANDARD" : parcel.serviceType();
        var details = new LinkedHashMap<String,Object>();
        details.put("receiverName", order.receiver().name()); details.put("receiverPhone", order.receiver().phone());
        details.put("pickupAddressLine1", order.pickup().address()); details.put("pickupAddressLine2", null);
        details.put("pickupCity", order.pickup().city());
        details.put("deliveryAddressLine1", order.receiver().address()); details.put("deliveryAddressLine2", null);
        details.put("deliveryCity", order.receiver().city()); details.put("weight", parcel.weightKg());
        details.put("size", size); details.put("parcelType", service);
        String hash;
        try { hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(order))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        try {
            var response = client.post().uri(PATH).headers(headers -> headers.setBearerAuth(bearer))
                    .body(new IntegrationPayload(order.merchantOrderReference(), hash, details))
                    .retrieve().toEntity(DownstreamOrder.class);
            if (!response.getStatusCode().is2xxSuccessful()) throw unavailable();
            return convert(response.getBody(), order.merchantOrderReference());
        } catch (RestClientResponseException exception) { throw translated(exception); }
        catch (RestClientException exception) { throw unavailable(); }
    }
    @Override public Optional<MerchantOrderResponse> current(String merchantId, String reference) {
        String bearer = token(merchantId);
        try {
            var response = client.get().uri(PATH + "/by-reference?reference={reference}", reference).headers(headers -> headers.setBearerAuth(bearer))
                    .retrieve().toEntity(DownstreamOrder.class);
            if (!response.getStatusCode().is2xxSuccessful()) throw unavailable();
            return Optional.of(convert(response.getBody(), reference));
        } catch (HttpClientErrorException.NotFound exception) { return Optional.empty(); }
        catch (RestClientResponseException exception) { throw translated(exception); }
        catch (RestClientException exception) { throw unavailable(); }
    }
    @Override public Optional<MerchantOrderStore.Page> list(String merchantId, int page, int size) {
        String bearer = token(merchantId);
        try {
            var response = client.get().uri(PATH + "?page={page}&size={size}", page, size)
                    .headers(headers -> headers.setBearerAuth(bearer)).retrieve().toEntity(DownstreamPage.class);
            var result = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || result == null || result.items() == null
                    || result.total() < 0 || result.page() != page || result.size() != size || result.items().size() > size) throw unavailable();
            return Optional.of(new MerchantOrderStore.Page(result.items().stream()
                    .map(item -> convert(item, item.merchantOrderReference())).toList(), result.total(), page, size));
        } catch (RestClientResponseException exception) { throw translated(exception); }
        catch (RestClientException exception) { throw unavailable(); }
    }
    private static MerchantOrderResponse convert(DownstreamOrder result, String reference) {
        if (result == null || reference == null || !reference.equals(result.merchantOrderReference())
                || result.parcelId() == null || result.trackingNumber() == null || result.trackingNumber().isBlank()
                || result.status() == null || !Set.of("CREATED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","CANCELLED").contains(result.status())
                || result.charge() == null || result.charge().signum() < 0 || result.createdAt() == null) throw unavailable();
        return new MerchantOrderResponse(reference, result.trackingNumber(), result.status(),
                new MerchantOrderResponse.ParcelSnapshot(result.parcelId().toString(), result.charge(), result.createdAt(),
                        result.weightKg(), result.size(), result.serviceType()));
    }
    private static ResponseStatusException translated(RestClientResponseException exception) {
        return switch (exception.getStatusCode().value()) {
            case 400 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parcel details are invalid");
            case 403 -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant parcel access is not available");
            case 409 -> new ResponseStatusException(HttpStatus.CONFLICT, "Order reference already used with different details");
            default -> unavailable();
        };
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is unavailable; retry the same order reference");
    }
    private record DownstreamOrder(String merchantOrderReference, String trackingNumber, String status,
            java.util.UUID parcelId, java.math.BigDecimal charge, java.time.Instant createdAt,
            java.math.BigDecimal weightKg, String size, String serviceType) { }
    private record DownstreamPage(List<DownstreamOrder> items, long total, int page, int size) { }
    private record IntegrationPayload(String merchantOrderReference, String sourceRequestHash, Map<String,Object> parcel) {
        @Override public String toString() { return "IntegrationPayload[merchantOrderReference=" + merchantOrderReference + "]"; }
    }
}
