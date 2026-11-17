package com.swiftdrop.dispatch.event;

import com.swiftdrop.dispatch.config.DispatchRabbitConfig;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.service.DispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores every parcel status event from dispatch's own queue. A message that can
 * never be processed (unreadable or invalid) is rejected without requeue, so the
 * queue's dead-letter exchange takes it and the listener keeps running.
 * Other failures (for example the database being down) are retried by the
 * listener's retry settings before the message is dead-lettered.
 */
@Component
public class ParcelStatusChangedConsumer {
    private static final Logger log = LoggerFactory.getLogger(ParcelStatusChangedConsumer.class);

    private final ObjectMapper objectMapper;
    private final DispatchService dispatch;

    public ParcelStatusChangedConsumer(ObjectMapper objectMapper, DispatchService dispatch) {
        this.objectMapper = objectMapper;
        this.dispatch = dispatch;
    }

    @RabbitListener(queues = DispatchRabbitConfig.STATUS_QUEUE)
    public void consume(byte[] message) {
        ParcelStatusChangedEvent event;
        try {
            event = objectMapper.readValue(message, ParcelStatusChangedEvent.class);
        } catch (JacksonException exception) {
            log.warn("Rejected an unreadable parcel status event: {}", exception.getOriginalMessage());
            throw new AmqpRejectAndDontRequeueException("Unreadable parcel status event", exception);
        }
        try {
            boolean stored = dispatch.applyStatusEvent(event);
            if (!stored) log.debug("Ignored duplicate parcel status event {}", event.eventId());
        } catch (DispatchException exception) {
            if (exception.getStatus() != HttpStatus.BAD_REQUEST) throw exception;
            log.warn("Rejected invalid parcel status event {}", event == null ? null : event.eventId());
            throw new AmqpRejectAndDontRequeueException("Invalid parcel status event", exception);
        }
    }
}
