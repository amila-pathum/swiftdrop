package com.swiftdrop.parcel.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.parcel.config.ParcelRabbitConfig;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.service.ParcelService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies the statuses a courier confirms in the Courier Service (picked up, in
 * transit, out for delivery, delivered, failed) to the parcel. The Parcel Service
 * owns the status, so its usual parcel.status-changed event then reaches the
 * customer's notifications, merchant webhooks and the other services.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.events.courier-status-consumer-enabled", havingValue = "true", matchIfMissing = true)
public class CourierDeliveryStatusConsumer {
    public static final String ROUTING_KEY = "courier.delivery-status-changed.v1";
    public static final String QUEUE = "parcel.courier-delivery-status-changed";
    private static final Logger log = LoggerFactory.getLogger(CourierDeliveryStatusConsumer.class);

    private final ParcelService parcelService;
    private final ObjectMapper objectMapper;

    public CourierDeliveryStatusConsumer(ParcelService parcelService, ObjectMapper objectMapper) {
        this.parcelService = parcelService;
        this.objectMapper = objectMapper;
    }

    /** The fields of courier-service's CourierDeliveryStatusChanged event this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourierDeliveryStatusChanged(UUID eventId, String eventType, UUID submissionId, UUID parcelId,
            String trackingNumber, Long courierUserId, String oldStatus, String newStatus) { }

    @Bean
    Declarables courierDeliveryStatusQueue() {
        TopicExchange exchange = new TopicExchange(ParcelRabbitConfig.EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(QUEUE).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
        return new Declarables(exchange, queue, binding);
    }

    @RabbitListener(queues = QUEUE)
    public void receive(byte[] body) {
        CourierDeliveryStatusChanged event;
        ParcelStatus status;
        try {
            event = objectMapper.readValue(body, CourierDeliveryStatusChanged.class);
            status = ParcelStatus.valueOf(String.valueOf(event.newStatus()));
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid courier delivery status event", invalid);
        }
        if (event.parcelId() == null || event.submissionId() == null || event.courierUserId() == null) {
            throw new AmqpRejectAndDontRequeueException("Courier delivery status event is missing identifiers");
        }
        boolean applied = parcelService.applyCourierStatus(
            event.parcelId(), event.courierUserId(), status, event.submissionId());
        if (applied) {
            log.info("Applied courier status {} to parcel {} (submission {})",
                status, event.trackingNumber(), event.submissionId());
        } else {
            log.warn("Courier status {} for parcel {} not applied: unknown parcel, other courier, "
                + "duplicate or transition not allowed (submission {})",
                status, event.parcelId(), event.submissionId());
        }
    }
}
