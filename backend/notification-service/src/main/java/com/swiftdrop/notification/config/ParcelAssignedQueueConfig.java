package com.swiftdrop.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares notification.parcel-assigned (also in infrastructure/docker/rabbitmq/definitions.json)
 * so a broker started before the queue was added to the definitions still gets it.
 * Arguments must match the definitions file exactly, or RabbitMQ refuses the declaration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.notifications.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class ParcelAssignedQueueConfig {

    @Bean
    Declarables parcelAssignedNotificationQueue() {
        TopicExchange exchange = new TopicExchange(RabbitMQConfig.EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(RabbitMQConfig.ASSIGNED_QUEUE)
                .deadLetterExchange(RabbitMQConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConfig.DEAD_LETTER_ROUTING_KEY)
                .build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(RabbitMQConfig.ASSIGNED_ROUTING_KEY);
        return new Declarables(exchange, queue, binding);
    }
}
