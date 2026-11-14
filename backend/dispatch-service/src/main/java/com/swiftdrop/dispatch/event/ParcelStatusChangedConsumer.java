package com.swiftdrop.dispatch.event;

import com.swiftdrop.dispatch.config.DispatchRabbitConfig;
import com.swiftdrop.dispatch.service.DispatchService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParcelStatusChangedConsumer {
    private final ObjectMapper objectMapper;
    private final DispatchService dispatch;

    public ParcelStatusChangedConsumer(ObjectMapper objectMapper, DispatchService dispatch) {
        this.objectMapper = objectMapper;
        this.dispatch = dispatch;
    }

    @RabbitListener(queues = DispatchRabbitConfig.STATUS_QUEUE)
    public void consume(byte[] message) throws Exception {
        ParcelStatusChangedEvent event = objectMapper.readValue(message, ParcelStatusChangedEvent.class);
        dispatch.applyStatusEvent(event);
    }
}
