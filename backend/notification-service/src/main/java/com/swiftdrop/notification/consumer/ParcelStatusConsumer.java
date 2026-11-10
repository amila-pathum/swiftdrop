
package com.swiftdrop.notification.consumer;

import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.event.ParcelStatusChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParcelStatusConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParcelStatusConsumer.class);
    private final ObjectMapper objectMapper;

    public ParcelStatusConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RabbitListener( queues = RabbitMQConfig.NOTIFICATION_QUEUE )
    public void consume(byte[] message) throws Exception {

        ParcelStatusChangedEvent event =
                objectMapper.readValue(
                        message,
                        ParcelStatusChangedEvent.class
                );

        log.info("Received parcel status event");
        log.info("Event ID: {}", event.eventId());
        log.info("Tracking Number: {}", event.trackingNumber());
        log.info("Old Status: {}", event.oldStatus());
        log.info("New Status: {}", event.newStatus());
    }
}
