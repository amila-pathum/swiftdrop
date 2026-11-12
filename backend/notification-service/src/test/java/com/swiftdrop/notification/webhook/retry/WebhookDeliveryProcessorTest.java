package com.swiftdrop.notification.webhook.retry;

import com.swiftdrop.notification.webhook.delivery.WebhookHttpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookDeliveryProcessorTest {
    private final UUID id=UUID.randomUUID(), event=UUID.randomUUID();
    private final byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final MutableClock clock=new MutableClock();
    private WebhookHttpSender sender;
    private MerchantWebhookSettingsLookup settings;
    private WebhookDeliveryProcessor processor;

    @BeforeEach void setUp() {
        sender=mock(WebhookHttpSender.class);
        settings=mock(MerchantWebhookSettingsLookup.class);
        when(settings.findForMerchant("m1")).thenReturn(Optional.of(
                new MerchantWebhookSettingsLookup.Settings(
                    URI.create("https://merchant.example/webhook"),"test-secret",true)));
        processor=new WebhookDeliveryProcessor(settings,sender,new WebhookRetryPolicy(),
                clock,Set.of("https://merchant.example"));
    }
    private WebhookDelivery delivery(int count) {
        return new WebhookDelivery(id,event,"m1",body,count);
    }
    @Test void failsTwiceThenSucceedsWithoutSleeping() throws Exception {
        when(sender.send(any(),anyString(),anyString(),any())).thenReturn(
                new WebhookHttpSender.SendResult(false,500,"HTTP_ERROR"),
                new WebhookHttpSender.SendResult(false,500,"HTTP_ERROR"),
                new WebhookHttpSender.SendResult(true,204,null));
        var first=processor.attempt(delivery(0));
        assertEquals("RETRY_SCHEDULED",first.state());
        assertEquals(clock.instant().plusSeconds(60),first.nextAttemptAt());
        clock.now=first.nextAttemptAt();
        var second=processor.attempt(delivery(1));
        assertEquals(clock.instant().plusSeconds(300),second.nextAttemptAt());
        clock.now=second.nextAttemptAt();
        var third=processor.attempt(delivery(2));
        assertEquals("DELIVERED",third.state());
        assertNull(third.nextAttemptAt());
        verify(sender,times(3)).send(any(),eq("test-secret"),eq(event.toString()),any());
    }
    @Test void fifthFailureIsTerminal() throws Exception {
        when(sender.send(any(),anyString(),anyString(),any())).thenReturn(
                new WebhookHttpSender.SendResult(false,503,"HTTP_ERROR"));
        var result=processor.attempt(delivery(4));
        assertEquals("FAILED",result.state());
        assertNull(result.nextAttemptAt());
    }
    @Test void disabledWebhookIsSkipped() throws Exception {
        when(settings.findForMerchant("m1")).thenReturn(Optional.of(
                new MerchantWebhookSettingsLookup.Settings(
                    URI.create("https://merchant.example/webhook"),"test-secret",false)));
        assertEquals("SKIPPED",processor.attempt(delivery(0)).state());
        verifyNoInteractions(sender);
    }
    @Test void unapprovedOriginIsNotContacted() throws Exception {
        when(settings.findForMerchant("m1")).thenReturn(Optional.of(
                new MerchantWebhookSettingsLookup.Settings(
                    URI.create("https://another.example/"),"test-secret",true)));
        var result=processor.attempt(delivery(0));
        assertEquals("ENDPOINT_NOT_ALLOWED",result.failureCode());
        assertEquals("FAILED",result.state());
        verifyNoInteractions(sender);
    }
    @Test void dependencyFailureDoesNotLeakSecrets() throws Exception {
        when(settings.findForMerchant("m1"))
                .thenThrow(new IllegalStateException("password=do-not-expose"));
        var result=processor.attempt(delivery(0));
        assertEquals("RETRY_SCHEDULED",result.state());
        assertEquals("DEPENDENCY_ERROR",result.failureCode());
        assertFalse(result.toString().contains("do-not-expose"));
        verifyNoInteractions(sender);
    }
    @Test void interruptionPropagates() throws Exception {
        when(sender.send(any(),anyString(),anyString(),any()))
                .thenThrow(new InterruptedException("stopping"));
        assertThrows(InterruptedException.class,() -> processor.attempt(delivery(0)));
    }
    private static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-13T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
