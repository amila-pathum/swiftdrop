package com.swiftdrop.merchant.merchantapi.service;

import com.swiftdrop.merchant.merchantapi.client.ParcelOrderClient;
import com.swiftdrop.merchant.merchantapi.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantOrderService {

    private final ObjectProvider<ParcelOrderClient> parcelClients;
    private final MerchantOrderStore store;
    private final tools.jackson.databind.ObjectMapper mapper;

    public MerchantOrderService(
            ObjectProvider<ParcelOrderClient> parcelClients,
            MerchantOrderStore store, tools.jackson.databind.ObjectMapper mapper
    ) {
        this.parcelClients = parcelClients;
        this.store = store;
        this.mapper = mapper;
    }

    public MerchantOrderResponse createOrder(
            String merchantId,
            CreateMerchantOrderRequest request
    ) {
        String hash;
        try {
            hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(request)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        // Returning a completed duplicate does not require an available downstream client.
        var existing = store.findReservation(merchantId, request.merchantOrderReference());
        if (existing.isPresent()) {
            var saved = completedResult(existing.get(), hash);
            if (saved != null) return saved;
        }
        ParcelOrderClient client = parcelClients.getIfAvailable();
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel order creation is unavailable");
        }
        if (existing.isEmpty() && !store.reserve(merchantId, request.merchantOrderReference(), hash)) {
            var saved = completedResult(store.reservation(merchantId, request.merchantOrderReference()), hash);
            if (saved != null) return saved;
            if (!client.idempotentCreation()) throw pending();
        }
        if (existing.isPresent() && !client.idempotentCreation()) throw pending();
        // The downstream transaction now deduplicates this exact reference and body, even after a timeout.
        var result = client.createOrder(merchantId, request);
        if (result == null || !request.merchantOrderReference().equals(result.merchantOrderReference())
                || result.trackingNumber() == null || result.trackingNumber().isBlank()
                || result.status() == null || result.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid parcel response; reconciliation required");
        }
        store.complete(merchantId, result);
        return result;
    }

    private MerchantOrderResponse completedResult(MerchantOrderStore.Reservation existing, String hash) {
        if (!hash.equals(existing.hash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order reference already used with different details");
        }
        return existing.result();
    }

    private ResponseStatusException pending() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Order creation is pending reconciliation; do not submit a new reference");
    }

    public MerchantOrderStore.Page list(String merchant, int page, int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        var client = parcelClients.getIfAvailable();
        if (client != null && client.idempotentCreation()) {
            return client.list(merchant, page, size).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel orders are unavailable"));
        }
        return store.list(merchant,page,size);
    }
    public MerchantOrderResponse detail(String merchant, String tracking) {
        var saved = store.detail(merchant,tracking).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        var client = parcelClients.getIfAvailable();
        if (client != null && client.idempotentCreation()) return client.current(merchant, saved.merchantOrderReference())
                .filter(current -> tracking.equals(current.trackingNumber()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return saved;
    }
}
