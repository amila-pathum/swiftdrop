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

    public MerchantOrderService(
            ObjectProvider<ParcelOrderClient> parcelClients
    ) {
        this.parcelClients = parcelClients;
    }

    public MerchantOrderResponse createOrder(
            String merchantId,
            CreateMerchantOrderRequest request
    ) {
        ParcelOrderClient client = parcelClients.getIfAvailable();

        if (client == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel order creation is unavailable"
            );
        }

        return client.createOrder(merchantId, request);
    }
}