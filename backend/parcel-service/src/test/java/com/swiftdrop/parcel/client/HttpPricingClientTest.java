package com.swiftdrop.parcel.client;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpPricingClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpPricingClient client = new HttpPricingClient(builder, "http://pricing.test", "/quote");
    private final CreateParcelRequest request = new CreateParcelRequest("Nimal", "0771234567",
            "Colombo", "Kandy", new BigDecimal("2.500"), "MEDIUM", "STANDARD");

    @Test
    void usesConfiguredRouteAndReadsCharge() {
        server.expect(requestTo("http://pricing.test/quote")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("weight").value(2.5))
            .andExpect(jsonPath("pickupAddress").value("Colombo"))
            .andRespond(withSuccess("{\"charge\":450.00}", MediaType.APPLICATION_JSON));
        assertThat(client.quote(request)).isEqualByComparingTo("450.00");
        server.verify();
    }

    @Test
    void upstreamFailureReturnsUnavailableWithoutInventingPrice() {
        server.expect(requestTo("http://pricing.test/quote")).andRespond(withServerError());
        assertThatThrownBy(() -> client.quote(request)).isInstanceOfSatisfying(
                ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingChargeReturnsBadGateway() {
        server.expect(requestTo("http://pricing.test/quote"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.quote(request)).isInstanceOfSatisfying(
                ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
