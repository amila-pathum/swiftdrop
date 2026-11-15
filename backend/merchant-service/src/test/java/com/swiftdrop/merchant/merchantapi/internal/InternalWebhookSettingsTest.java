package com.swiftdrop.merchant.merchantapi.internal;

import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.repository.*;
import com.swiftdrop.merchant.service.MerchantWebhookService;
import com.swiftdrop.merchant.dto.request.UpdateMerchantWebhookRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InternalWebhookSettingsTest {
    @Test void existingEncryptionRoundTripsOnlyForApprovedEnabledMerchant() {
        var merchants = mock(MerchantRepository.class);
        var settings = mock(MerchantWebhookSettingsRepository.class);
        var merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(7L);
        when(merchant.getStatus()).thenReturn(MerchantStatus.APPROVED);
        when(merchants.findByUserId(9001L)).thenReturn(Optional.of(merchant));
        when(merchants.findById(7L)).thenReturn(Optional.of(merchant));
        when(settings.findByMerchantId(7L)).thenReturn(Optional.empty());
        when(settings.saveAndFlush(any())).thenAnswer(call -> {
            MerchantWebhookSettings saved = call.getArgument(0);
            when(settings.findByMerchantId(7L)).thenReturn(Optional.of(saved));
            return saved;
        });
        var service = new MerchantWebhookService(merchants,settings,
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        var created = service.configure(9001L,new UpdateMerchantWebhookRequest("https://example.test/webhook",true));
        assertEquals(created.signingSecret(),service.getInternalSettings(7L).signingSecret());
        assertTrue(service.getInternalSettings(7L).enabled());
        when(merchant.getStatus()).thenReturn(MerchantStatus.PENDING);
        assertFalse(service.getInternalSettings(7L).enabled());
        assertNull(service.getInternalSettings(7L).signingSecret());
        when(merchant.getStatus()).thenReturn(MerchantStatus.APPROVED);
        settings.findByMerchantId(7L).orElseThrow().setEnabled(false);
        assertFalse(service.getInternalSettings(7L).enabled());
    }
}
