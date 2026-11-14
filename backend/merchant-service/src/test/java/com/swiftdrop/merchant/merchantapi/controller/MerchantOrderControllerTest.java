package com.swiftdrop.merchant.merchantapi.controller;

import com.swiftdrop.merchant.config.SecurityConfig;
import com.swiftdrop.merchant.merchantapi.client.ParcelOrderClient;
import com.swiftdrop.merchant.merchantapi.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;
import com.swiftdrop.merchant.merchantapi.exception.MerchantApiExceptionHandler;
import com.swiftdrop.merchant.merchantapi.security.MerchantApiKeyLookup;
import com.swiftdrop.merchant.merchantapi.security.MerchantApiSecurityConfig;
import com.swiftdrop.merchant.merchantapi.security.MerchantKeyDetails;
import com.swiftdrop.merchant.merchantapi.service.MerchantOrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(classes = {
        SecurityConfig.class,
        MerchantApiSecurityConfig.class,
        MerchantOrderService.class,
        MerchantOrderController.class,
        MerchantApiExceptionHandler.class,
        MerchantOrderControllerTest.TestConfig.class
})
@TestPropertySource(properties = {
        "swiftdrop.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class MerchantOrderControllerTest {

    private static final String URL =
            "/api/merchants/integration/orders";

    private static final String ORDER_JSON = """
            {
              "merchantOrderReference": "SHOP-1001",
              "pickup": {
                "name": "Test Sender",
                "phone": "0771234567",
                "address": "Test pickup address",
                "city": "Colombo"
              },
              "receiver": {
                "name": "Test Receiver",
                "phone": "0711234567",
                "address": "Test delivery address",
                "city": "Kandy"
              },
              "parcel": {
                "description": "Books",
                "weightKg": 2.5,
                "lengthCm": 30,
                "widthCm": 20,
                "heightCm": 10
              }
            }
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MerchantApiKeyLookup keyLookup;

    @Autowired
    private ParcelOrderClient parcelClient;

    private MockMvc mockMvc;
    @Autowired private com.swiftdrop.merchant.merchantapi.service.MerchantOrderStore store;

    @BeforeEach
    void setUp() {
        reset(keyLookup, parcelClient);
        reset(store);
        when(store.reserve(anyString(), anyString(), anyString())).thenReturn(true);

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private void approveMerchant() {
        when(keyLookup.findByApiKey("test-key"))
                .thenReturn(Optional.of(
                        new MerchantKeyDetails("merchant-1", true)
                ));
    }

    @Test
    void approvedMerchantCanSubmitOrder() throws Exception {
        approveMerchant();

        when(parcelClient.createOrder(
                eq("merchant-1"),
                any(CreateMerchantOrderRequest.class)
        )).thenReturn(new MerchantOrderResponse(
                "SHOP-1001", "SD-TEST-1001", "Created"
        ));

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber")
                        .value("SD-TEST-1001"))
                .andExpect(jsonPath("$.merchantOrderReference")
                        .value("SHOP-1001"));

        verify(parcelClient).createOrder(
                eq("merchant-1"),
                argThat(request -> {
                    assertEquals(
                            "SHOP-1001",
                            request.merchantOrderReference()
                    );
                    return true;
                })
        );
    }

    @Test
    void missingKeyCannotCreateOrder() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parcelClient);
    }

    @Test
    void unapprovedMerchantCannotCreateOrder() throws Exception {
        when(keyLookup.findByApiKey("test-key"))
                .thenReturn(Optional.of(
                        new MerchantKeyDetails("merchant-1", false)
                ));

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(parcelClient);
    }

    @Test
    void invalidNestedParcelDataIsRejected() throws Exception {
        approveMerchant();

        String invalidOrder = ORDER_JSON.replace(
                "\"weightKg\": 2.5",
                "\"weightKg\": 0"
        );

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidOrder))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid order details"));

        verifyNoInteractions(parcelClient);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        approveMerchant();

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid JSON request"));

        verifyNoInteractions(parcelClient);
    }

    @Test
    void parcelServiceUnavailableReturns503() throws Exception {
        approveMerchant();

        when(parcelClient.createOrder(
                anyString(),
                any(CreateMerchantOrderRequest.class)
        )).thenThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Parcel order creation is unavailable"
        ));

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error")
                        .value("Parcel order creation is unavailable"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean
        com.swiftdrop.merchant.merchantapi.service.MerchantOrderStore orderStore() {
            return mock(com.swiftdrop.merchant.merchantapi.service.MerchantOrderStore.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MerchantApiKeyLookup keyLookup() {
            return mock(MerchantApiKeyLookup.class);
        }

        @Bean
        ParcelOrderClient parcelOrderClient() {
            return mock(ParcelOrderClient.class);
        }
    }
}
