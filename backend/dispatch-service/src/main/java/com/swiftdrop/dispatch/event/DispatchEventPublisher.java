package com.swiftdrop.dispatch.event;

import com.swiftdrop.dispatch.config.DispatchRabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class DispatchEventPublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;

    public DispatchEventPublisher(RabbitTemplate rabbit, ObjectMapper objectMapper) {
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAssignment(ParcelAssignedEvent event) throws IOException {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(DispatchRabbitConfig.EXCHANGE, DispatchRabbitConfig.ASSIGNMENT_ROUTING_KEY,
            new Message(objectMapper.writeValueAsBytes(event), properties));
    }
}
