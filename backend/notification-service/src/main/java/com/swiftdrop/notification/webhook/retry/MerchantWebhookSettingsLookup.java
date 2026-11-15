package com.swiftdrop.notification.webhook.retry;

import java.net.URI;
import java.util.Optional;

/** Member 8's authenticated internal API adapter implements this interface.
 * Never implement this by directly accessing merchant_db. */
public interface MerchantWebhookSettingsLookup {
    Optional<Settings> findForMerchant(String merchantId);

    record Settings(URI endpoint, String signingSecret, boolean enabled, java.util.Set<String> events) {
        public Settings(URI endpoint, String signingSecret, boolean enabled) { this(endpoint, signingSecret, enabled, null); }
        public Settings { if (events != null) events = java.util.Set.copyOf(events); }
        @Override
        public String toString() {
            return "WebhookSettings[endpoint=redacted, signingSecret=redacted, enabled="
                    + enabled + "]";
        }
    }
}
