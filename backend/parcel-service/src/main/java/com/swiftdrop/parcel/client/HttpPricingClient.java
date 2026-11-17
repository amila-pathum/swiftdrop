package com.swiftdrop.parcel.client;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Component
public class HttpPricingClient implements PricingClient {
    private final RestClient client;
    private final String quotePath;

    public HttpPricingClient(RestClient.Builder builder,
        @Value("${swiftdrop.pricing.base-url:http://localhost:8086}") String baseUrl,
        @Value("${swiftdrop.pricing.quote-path:/api/pricing/quote}") String quotePath) {
        this.client = builder.baseUrl(baseUrl).build();
        this.quotePath = quotePath;
    }

    // Send parcel size for pricing surcharges; accept amount and the legacy charge field.
    // Do not replace this with a locally invented price when pricing is unavailable.
    @Override
    public BigDecimal quote(CreateParcelRequest request, String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authenticated access token is required for pricing");
        }
        try {
            QuoteResponse response = client.post().uri(quotePath)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(new QuoteRequest(request.pickupCity(), request.deliveryCity(),
                                       request.weight(), request.parcelType(), request.size()))
                .retrieve().body(QuoteResponse.class);
            if (response == null || response.amount() == null ||
                response.amount().signum() < 0 || response.amount().stripTrailingZeros().scale() > 2 ||
                response.amount().compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pricing service returned an invalid charge");
            }
            // Normalize representation (450.0000 -> 450.00) without rounding or inventing a price.
            return response.amount().setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Pricing service unavailable", exception);
        }
    }

    private record QuoteRequest(String pickupCity, String deliveryCity,
                                BigDecimal weight, String serviceType, String size) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    private record QuoteResponse(@com.fasterxml.jackson.annotation.JsonAlias("charge") BigDecimal amount) {}
}
