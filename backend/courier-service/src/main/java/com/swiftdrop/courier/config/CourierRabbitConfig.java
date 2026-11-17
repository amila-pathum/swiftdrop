package com.swiftdrop.courier.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CourierRabbitConfig {
    public static final String EXCHANGE = "swiftdrop.events";
    public static final String QUEUE = "courier.delivery-events";
    public static final String ASSIGNED = "parcel.assigned";
    public static final String STATUS = "parcel.status-changed";
    public static final String SNAPSHOT = "parcel.delivery-snapshot.v1";

    @Bean
    Declarables courierDeliveryTopology() {
        var exchange = new TopicExchange(EXCHANGE, true, false);
        var dead = new DirectExchange("swiftdrop.courier.dlx", true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(dead.getName()).deadLetterRoutingKey("failed").build();
        var dlq = QueueBuilder.durable(QUEUE + ".dlq").build();
        return new Declarables(exchange, dead, queue, dlq,
            BindingBuilder.bind(queue).to(exchange).with(ASSIGNED),
            BindingBuilder.bind(queue).to(exchange).with(STATUS),
            BindingBuilder.bind(queue).to(exchange).with(SNAPSHOT),
            BindingBuilder.bind(dlq).to(dead).with("failed"));
    }
}
