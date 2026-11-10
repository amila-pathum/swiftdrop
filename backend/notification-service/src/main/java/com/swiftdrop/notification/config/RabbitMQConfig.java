
package com.swiftdrop.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitMQConfig {

        public static final String EXCHANGE = "swiftdrop.events";
        public static final String ROUTING_KEY = "parcel.status-changed";
        public static final String NOTIFICATION_QUEUE = "notification.parcel-status-changed";
        public static final String WEBHOOK_QUEUE = "webhook.parcel-status-changed";
        public static final String REPORTS_QUEUE = "reports.parcel-status-changed";
        public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx";
        public static final String DEAD_LETTER_QUEUE = "notification.dlq";
        public static final String DEAD_LETTER_ROUTING_KEY = "notification.failed";

        // Main topic exchange
        @Bean
        public TopicExchange parcelExchange() {
                return new TopicExchange(EXCHANGE, true, false);
        }

        // Dead-letter exchange
        @Bean
        public TopicExchange deadLetterExchange() {
                return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
        }

        // Notification queue
        @Bean
        public Queue notificationQueue() {
                return QueueBuilder
                                .durable(NOTIFICATION_QUEUE)
                                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                                .build();
        }

        // Webhook queue
        @Bean
        public Queue webhookQueue() {
                return QueueBuilder
                                .durable(WEBHOOK_QUEUE)
                                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                                .build();
        }

        // Reports queue
        @Bean
        public Queue reportsQueue() {
                return QueueBuilder
                                .durable(REPORTS_QUEUE)
                                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                                .build();
        }

        // Dead-letter queue
        @Bean
        public Queue deadLetterQueue() {
                return QueueBuilder
                                .durable(DEAD_LETTER_QUEUE)
                                .build();
        }

        // Connect notification queue to main exchange
        @Bean
        public Binding notificationBinding(
                        @Qualifier("notificationQueue") Queue notificationQueue,
                        @Qualifier("parcelExchange") TopicExchange parcelExchange) {

                return BindingBuilder
                                .bind(notificationQueue)
                                .to(parcelExchange)
                                .with(ROUTING_KEY);
        }

        // Connect webhook queue to main exchange
        @Bean
        public Binding webhookBinding(
                        @Qualifier("webhookQueue") Queue webhookQueue,
                        @Qualifier("parcelExchange") TopicExchange parcelExchange) {

                return BindingBuilder
                                .bind(webhookQueue)
                                .to(parcelExchange)
                                .with(ROUTING_KEY);
        }

        // Connect reports queue to main exchange
        @Bean
        public Binding reportsBinding(
                        @Qualifier("reportsQueue") Queue reportsQueue,
                        @Qualifier("parcelExchange") TopicExchange parcelExchange) {

                return BindingBuilder
                                .bind(reportsQueue)
                                .to(parcelExchange)
                                .with(ROUTING_KEY);
        }

        // Connect dead-letter queue to dead-letter exchange
        @Bean
        public Binding deadLetterBinding(
                        @Qualifier("deadLetterQueue") Queue deadLetterQueue,
                        @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {

                return BindingBuilder
                                .bind(deadLetterQueue)
                                .to(deadLetterExchange)
                                .with(DEAD_LETTER_ROUTING_KEY);
        }
}
