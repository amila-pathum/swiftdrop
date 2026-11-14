package com.swiftdrop.notification.webhook.portal;

import com.swiftdrop.notification.webhook.retry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "swiftdrop.internal.webhook-token=test-internal-token-at-least-32-characters",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class WebhookManagementSecurityTest {
    static final String PATH = "/internal/webhooks/merchants/1/deliveries";
    static final String TOKEN = "test-internal-token-at-least-32-characters";
    @Autowired MockMvc mvc;
    @MockitoBean WebhookDeliveryStore store;
    @MockitoBean MerchantWebhookSettingsLookup settings;

    @Test void missingWrongAndJwtOnlyCredentialsAreRejected() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-SwiftDrop-Internal-Token","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(jwt())).andExpect(status().isUnauthorized());
        verifyNoInteractions(store);
    }
    @Test void trustedCallerGetsScopedHistory() throws Exception {
        when(store.history("1",false,0,20)).thenReturn(new WebhookDeliveryStore.Page(List.of(),0,0,20));
        mvc.perform(get(PATH).header("X-SwiftDrop-Internal-Token",TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        verify(store).history("1",false,0,20);
    }
    @Test void foreignRecordCannotBeRetried() throws Exception {
        UUID id = UUID.randomUUID();
        when(store.detail("1",id)).thenReturn(Optional.empty());
        mvc.perform(post(PATH + "/" + id + "/retry").header("X-SwiftDrop-Internal-Token",TOKEN))
                .andExpect(status().isNotFound());
        verify(store,never()).retry(anyString(),any(),any());
    }
    @Test void disabledSettingsCannotQueueTest() throws Exception {
        when(settings.findForMerchant("1")).thenReturn(Optional.empty());
        mvc.perform(post(PATH + "/test").header("X-SwiftDrop-Internal-Token",TOKEN))
                .andExpect(status().isConflict());
        verifyNoInteractions(store);
    }
}
