package com.swiftdrop.merchant.merchantapi.controller;

import com.swiftdrop.merchant.merchantapi.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;
import com.swiftdrop.merchant.merchantapi.security.MerchantKeyDetails;
import com.swiftdrop.merchant.merchantapi.service.MerchantOrderService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchants/integration/orders")
public class MerchantOrderController {

    private final MerchantOrderService orderService;

    @GetMapping
    @PreAuthorize("hasAuthority('MERCHANT_API')")
    public com.swiftdrop.merchant.merchantapi.service.MerchantOrderStore.Page list(
            @AuthenticationPrincipal MerchantKeyDetails merchant,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return orderService.list(merchant.merchantId(),page,size);
    }

    @GetMapping("/{trackingNumber}")
    @PreAuthorize("hasAuthority('MERCHANT_API')")
    public MerchantOrderResponse detail(@AuthenticationPrincipal MerchantKeyDetails merchant,
            @PathVariable String trackingNumber) {
        return orderService.detail(merchant.merchantId(),trackingNumber);
    }

    public MerchantOrderController(MerchantOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MERCHANT_API')")
    public MerchantOrderResponse createOrder(
            @AuthenticationPrincipal MerchantKeyDetails merchant,
            @Valid @RequestBody CreateMerchantOrderRequest request
    ) {
        return orderService.createOrder(
                merchant.merchantId(),
                request
        );
    }
}
