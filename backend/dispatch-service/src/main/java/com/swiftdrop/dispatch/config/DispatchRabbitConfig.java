package com.swiftdrop.dispatch.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchRabbitConfig {
    public static final String EXCHANGE = "swiftdrop.events";
    public static final String ASSIGNMENT_ROUTING_KEY = "parcel.assigned";
    public static final String STATUS_QUEUE = "reports.parcel-status-changed";

    @Bean
    TopicExchange swiftDropEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
