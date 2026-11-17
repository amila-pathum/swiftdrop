package com.swiftdrop.merchant.merchantapi.client;

import com.swiftdrop.merchant.merchantapi.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;

public interface ParcelOrderClient {

    MerchantOrderResponse createOrder(
            String merchantId,
            CreateMerchantOrderRequest request
    );

    /** True only for a downstream endpoint that atomically deduplicates the same reference and body. */
    default boolean idempotentCreation() { return false; }
    default java.util.Optional<MerchantOrderResponse> current(String merchantId, String reference) { return java.util.Optional.empty(); }
    default java.util.Optional<com.swiftdrop.merchant.merchantapi.service.MerchantOrderStore.Page> list(
            String merchantId, int page, int size) { return java.util.Optional.empty(); }
}
