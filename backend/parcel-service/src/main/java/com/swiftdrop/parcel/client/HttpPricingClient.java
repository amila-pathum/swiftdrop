package com.swiftdrop.parcel.client;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    // PROVISIONAL: agree request fields, route and response field with Member 4.
    // Do not replace this with a locally invented price when pricing is unavailable.
    @Override
    public BigDecimal quote(CreateParcelRequest request) {
        try {
            QuoteResponse response = client.post().uri(quotePath)
                .body(new QuoteRequest(request.pickupAddress(), request.deliveryAddress(),
                                       request.weight(), request.size(), request.parcelType()))
                .retrieve().body(QuoteResponse.class);
            if (response == null || response.charge() == null ||
                response.charge().signum() < 0 || response.charge().scale() > 2 ||
                response.charge().compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pricing service returned an invalid charge");
            }
            return response.charge();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Pricing service unavailable", exception);
        }
    }

    private record QuoteRequest(String pickupAddress, String deliveryAddress,
                                BigDecimal weight, String size, String parcelType) {}
    private record QuoteResponse(BigDecimal charge) {}
}
