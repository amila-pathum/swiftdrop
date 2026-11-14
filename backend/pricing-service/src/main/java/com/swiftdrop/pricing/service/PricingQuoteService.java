package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.request.QuoteRequest;
import com.swiftdrop.pricing.dto.response.QuoteResponse;
import com.swiftdrop.pricing.entity.PricingRule;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.strategy.PricingCalculator;
import com.swiftdrop.pricing.strategy.PricingContext;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

@Service
public class PricingQuoteService {

    private final ZoneService zoneService;
    private final PricingRuleService pricingRuleService;
    private final PricingCalculator pricingCalculator;

    public PricingQuoteService(
            ZoneService zoneService,
            PricingRuleService pricingRuleService,
            PricingCalculator pricingCalculator) {
        this.zoneService = zoneService;
        this.pricingRuleService = pricingRuleService;
        this.pricingCalculator = pricingCalculator;
    }

    public QuoteResponse calculateQuote(QuoteRequest request) {
        String serviceType = request.serviceType() == null ? ""
                : request.serviceType().trim().toUpperCase(Locale.ROOT);
        if (!serviceType.equals("STANDARD") && !serviceType.equals("EXPRESS")) {
            throw new IllegalArgumentException("Service type must be STANDARD or EXPRESS");
        }

        zoneService.getZoneByCity(request.pickupCity());

        Zone deliveryZone =
                zoneService.getZoneByCity(request.deliveryCity());

        PricingRule pricingRule =
                pricingRuleService.getActivePricingRuleByZoneId(
                        deliveryZone.getId());

        PricingContext context = new PricingContext(
                request.weight(),
                pricingRule,
                serviceType.equals("EXPRESS")
        );

        BigDecimal amount = pricingCalculator.calculate(context).setScale(2, RoundingMode.HALF_UP);

        BigDecimal baseFee = pricingRule.getBaseFee().setScale(2, RoundingMode.HALF_UP);

        BigDecimal weightFee = request.weight()
                .multiply(pricingRule.getPricePerKg()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal expressFee = amount
                .subtract(baseFee)
                .subtract(weightFee).setScale(2, RoundingMode.HALF_UP);

        return new QuoteResponse(
                amount,
                "LKR",
                Map.of(
                        "baseFee", baseFee,
                        "weightFee", weightFee,
                        "expressFee", expressFee
                )
        );
    }
}