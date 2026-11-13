package com.swiftdrop.notification.webhook.retry;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookEventRecorderTest {
    @Test void customerParcelDoesNotCreateWebhookRecord() {
        var store=mock(WebhookDeliveryStore.class);
        var recorder=new WebhookEventRecorder(store,new ObjectMapper());
        assertTrue(recorder.record(UUID.randomUUID(),Instant.now(),null,
                "SD-1",null,"DELIVERED").isEmpty());
        verifyNoInteractions(store);
    }
    @Test void incompleteMerchantEventIsRejected() {
        var store=mock(WebhookDeliveryStore.class);
        var recorder=new WebhookEventRecorder(store,new ObjectMapper());
        assertThrows(IllegalArgumentException.class,() -> recorder.record(null,
                Instant.now(),"m1","SD-1","SHOP-1","DELIVERED"));
        verifyNoInteractions(store);
    }
    @Test void savesOnlyStatusDataAsWebhookPayload() {
        var store=mock(WebhookDeliveryStore.class);
        var mapper=new ObjectMapper();
        var recorder=new WebhookEventRecorder(store,mapper);
        UUID event=UUID.randomUUID(), delivery=UUID.randomUUID();
        when(store.enqueue(eq(event),eq("m1"),eq("SD-1"),eq("SHOP-1"),
                eq("DELIVERED"),any(),any())).thenReturn(delivery);
        assertEquals(Optional.of(delivery),recorder.record(event,
                Instant.parse("2026-11-13T00:00:00Z"),"m1","SD-1","SHOP-1","DELIVERED"));
        var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(store).enqueue(eq(event),eq("m1"),eq("SD-1"),eq("SHOP-1"),
                eq("DELIVERED"),bytes.capture(),any());
        var json=mapper.readTree(bytes.getValue());
        assertEquals(event.toString(),json.get("eventId").asText());
        assertEquals("DELIVERED",json.get("newStatus").asText());
        assertEquals(6,json.size());
        assertFalse(json.has("phone"));
        assertFalse(json.has("address"));
        assertFalse(json.has("signingSecret"));
    }
}
