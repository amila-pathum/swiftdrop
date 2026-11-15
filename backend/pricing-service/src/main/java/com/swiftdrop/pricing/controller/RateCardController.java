package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.request.PublishRatesRequest;
import com.swiftdrop.pricing.dto.response.RateCardResponse;
import com.swiftdrop.pricing.dto.response.RateVersionSummary;
import com.swiftdrop.pricing.service.RateCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The admin rate card: every zone pair's rates plus size surcharges, published as versions. */
@RestController
@RequestMapping("/api/pricing/rates")
@PreAuthorize("hasRole('ADMIN')")
public class RateCardController {

    private final RateCardService rateCardService;

    public RateCardController(RateCardService rateCardService) {
        this.rateCardService = rateCardService;
    }

    @GetMapping
    public RateCardResponse getRateCard() {
        return rateCardService.getRateCard();
    }

    @GetMapping("/versions")
    public List<RateVersionSummary> getVersions() {
        return rateCardService.getVersionHistory();
    }

    /** Publishes a new version; the response is the new rate card. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RateCardResponse publish(@Valid @RequestBody PublishRatesRequest request,
                                    Authentication authentication) {
        return rateCardService.publish(request, authentication.getName());
    }
}
