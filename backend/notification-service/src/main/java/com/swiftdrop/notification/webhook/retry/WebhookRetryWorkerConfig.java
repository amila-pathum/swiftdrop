package com.swiftdrop.notification.webhook.retry;

import com.swiftdrop.notification.webhook.delivery.WebhookHttpSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="swiftdrop.webhooks.worker-enabled",havingValue="true")
public class WebhookRetryWorkerConfig {
    @Bean
    WebhookDeliveryProcessor webhookDeliveryProcessor(MerchantWebhookSettingsLookup settings,
            @Value("${swiftdrop.webhooks.allowed-origins:}") String origins) {
        Set<String> allowed=Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        Clock clock=Clock.systemUTC();
        return new WebhookDeliveryProcessor(settings,
                new WebhookHttpSender(clock,Duration.ofSeconds(2),Duration.ofSeconds(5)),
                new WebhookRetryPolicy(),clock,allowed);
    }
    @Bean
    RetryPoller webhookRetryPoller(WebhookDeliveryStore store,WebhookDeliveryProcessor processor) {
        return new RetryPoller(store,processor);
    }
    public static final class RetryPoller {
        private final WebhookDeliveryStore store;
        private final WebhookDeliveryProcessor processor;
        RetryPoller(WebhookDeliveryStore store,WebhookDeliveryProcessor processor) {
            this.store=store;this.processor=processor;
        }
        @Scheduled(fixedDelayString="${swiftdrop.webhooks.poll-delay-ms:5000}")
        public void poll() {
            for (var id:store.due(java.time.Instant.now(),10)) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    store.processOne(id,java.time.Instant.now(),processor);
                } catch (RuntimeException exception) {
                    if (Thread.currentThread().isInterrupted()) return;
                    // Keep records due for another poll; do not expose payloads/secrets.
                    System.getLogger(RetryPoller.class.getName()).log(
                        System.Logger.Level.WARNING,"Webhook attempt could not be persisted");
                }
            }
        }
    }
}
