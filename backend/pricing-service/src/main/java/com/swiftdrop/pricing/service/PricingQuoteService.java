package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.request.QuoteRequest;
import com.swiftdrop.pricing.dto.response.QuoteResponse;
import com.swiftdrop.pricing.entity.ParcelSize;
import com.swiftdrop.pricing.entity.RateVersion;
import com.swiftdrop.pricing.entity.RouteRate;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.exception.RateNotFoundException;
import com.swiftdrop.pricing.strategy.PriceCalculation;
import com.swiftdrop.pricing.strategy.PricingCalculator;
import com.swiftdrop.pricing.strategy.PricingContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class PricingQuoteService {

    private final ZoneService zoneService;
    private final RateCardService rateCardService;
    private final PricingCalculator pricingCalculator;

    public PricingQuoteService(
            ZoneService zoneService,
            RateCardService rateCardService,
            PricingCalculator pricingCalculator) {
        this.zoneService = zoneService;
        this.rateCardService = rateCardService;
        this.pricingCalculator = pricingCalculator;
    }

    @Transactional(readOnly = true)
    public QuoteResponse calculateQuote(QuoteRequest request) {
        // Check the cheap, request-only fields before any lookups.
        String serviceType = request.serviceType() == null ? ""
                : request.serviceType().trim().toUpperCase(Locale.ROOT);
        if (!serviceType.equals("STANDARD") && !serviceType.equals("EXPRESS")) {
            throw new IllegalArgumentException("Service type must be STANDARD or EXPRESS");
        }
        ParcelSize size = ParcelSize.parse(request.size());

        Zone pickupZone = zoneService.getZoneByLocality(request.pickupCity());
        Zone deliveryZone = zoneService.getZoneByLocality(request.deliveryCity());

        RateVersion rates = rateCardService.currentVersion()
                .orElseThrow(() -> new RateNotFoundException("No delivery rates have been published yet"));
        RouteRate route = rateCardService.getRoute(rates, pickupZone, deliveryZone);

        PriceCalculation price = pricingCalculator.calculate(new PricingContext(
                request.weight(), size, serviceType.equals("EXPRESS"), route, rates));

        return new QuoteResponse(price.amount(), "LKR", price.breakdown());
    }
}
