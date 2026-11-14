package com.swiftdrop.merchant.merchantapi.service;

import com.swiftdrop.merchant.merchantapi.client.ParcelOrderClient;
import com.swiftdrop.merchant.merchantapi.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MerchantOrderServiceTest {
    MerchantOrderStore store;
    ParcelOrderClient client;
    MerchantOrderService service;
    final CreateMerchantOrderRequest request = new CreateMerchantOrderRequest("SHOP-1",null,null,null);
    @BeforeEach void setup() {
        store = mock(MerchantOrderStore.class); client = mock(ParcelOrderClient.class);
        var beans = new StaticListableBeanFactory(); beans.addBean("client",client);
        service = new MerchantOrderService(beans.getBeanProvider(ParcelOrderClient.class),store,new ObjectMapper());
    }
    @Test void persistedResultIsReturnedWithoutCallingParcelAgain() {
        var result = new MerchantOrderResponse("SHOP-1","SD-1","CREATED");
        when(store.reserve(eq("1"),eq("SHOP-1"),anyString())).thenAnswer(call -> {
            when(store.reservation("1","SHOP-1")).thenReturn(new MerchantOrderStore.Reservation(call.getArgument(2),result));
            return false;
        });
        assertEquals(result,service.createOrder("1",request));
        verifyNoInteractions(client);
    }
    @Test void changedRequestConflicts() {
        when(store.reservation("1","SHOP-1")).thenReturn(new MerchantOrderStore.Reservation("different",null));
        var failure = assertThrows(ResponseStatusException.class, () -> service.createOrder("1",request));
        assertEquals(409,failure.getStatusCode().value());
        verifyNoInteractions(client);
    }
    @Test void ambiguousRemoteFailureIsNotReplayed() {
        when(store.reserve(eq("1"),eq("SHOP-1"),anyString())).thenAnswer(call -> {
            when(store.reservation("1","SHOP-1")).thenReturn(new MerchantOrderStore.Reservation(call.getArgument(2),null));
            return true;
        });
        when(client.createOrder("1",request)).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class, () -> service.createOrder("1",request));
        when(store.reserve(anyString(),anyString(),anyString())).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.createOrder("1",request));
        verify(client,times(1)).createOrder("1",request);
    }
}
