package com.swiftdrop.notification.config;

import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitMQConfig {

        public static final String EXCHANGE = "swiftdrop.events";
        public static final String ROUTING_KEY = "parcel.status-changed";
        public static final String NOTIFICATION_QUEUE = "notification.parcel-status-changed";
        public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx";
        public static final String DEAD_LETTER_QUEUE = "notification.dlq";
        public static final String DEAD_LETTER_ROUTING_KEY = "notification.failed";

        private RabbitMQConfig() {

        }
}
