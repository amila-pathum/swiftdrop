package com.swiftdrop.parcel.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ParcelRabbitConfig {
    public static final String EXCHANGE = "swiftdrop.events";

    @Bean
    TopicExchange swiftDropEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
