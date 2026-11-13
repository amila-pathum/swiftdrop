package com.swiftdrop.merchant.merchantapi.client;

import com.swiftdrop.merchant.merchantapi.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;

public interface ParcelOrderClient {

    MerchantOrderResponse createOrder(
            String merchantId,
            CreateMerchantOrderRequest request
    );
}