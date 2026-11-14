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
        if (existing.isPresent()) return existingResult(existing.get(), hash);
        ParcelOrderClient client = parcelClients.getIfAvailable();
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel order creation is unavailable");
        }
        if (!store.reserve(merchantId, request.merchantOrderReference(), hash)) {
            return existingResult(store.reservation(merchantId, request.merchantOrderReference()), hash);
        }
        // Reservation is committed before HTTP. Never replay an ambiguous remote operation.
        var result = client.createOrder(merchantId, request);
        if (result == null || !request.merchantOrderReference().equals(result.merchantOrderReference())
                || result.trackingNumber() == null || result.trackingNumber().isBlank()
                || result.status() == null || result.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid parcel response; reconciliation required");
        }
        store.complete(merchantId, result);
        return result;
    }

    private MerchantOrderResponse existingResult(MerchantOrderStore.Reservation existing, String hash) {
        if (!hash.equals(existing.hash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order reference already used with different details");
        }
        if (existing.result() != null) return existing.result();
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Order creation is pending reconciliation; do not submit a new reference");
    }

    public MerchantOrderStore.Page list(String merchant, int page, int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return store.list(merchant,page,size);
    }
    public MerchantOrderResponse detail(String merchant, String tracking) {
        return store.detail(merchant,tracking).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
    }
}
