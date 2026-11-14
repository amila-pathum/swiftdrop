package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.request.QuoteRequest;
import com.swiftdrop.pricing.dto.response.QuoteResponse;
import com.swiftdrop.pricing.service.PricingQuoteService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
public class PricingQuoteController {

    private final PricingQuoteService pricingQuoteService;

    public PricingQuoteController(
            PricingQuoteService pricingQuoteService) {
        this.pricingQuoteService = pricingQuoteService;
    }

    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> calculateQuote(
            @Valid @RequestBody QuoteRequest request) {

        QuoteResponse response =
                pricingQuoteService.calculateQuote(request);

        return ResponseEntity.ok(response);
    }
}