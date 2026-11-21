package com.swiftdrop.courier.event;

import com.swiftdrop.courier.config.CourierRabbitConfig;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** At-least-once delivery. Broker confirms and returns prevent silently losing unrouted events. */
@Service
@ConditionalOnProperty(name="swiftdrop.events.publisher-enabled",havingValue="true",matchIfMissing=true)
public class CourierOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    public CourierOutboxPublisher(JdbcTemplate jdbc,RabbitTemplate rabbit,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.rabbit=rabbit;this.transactions=new TransactionTemplate(manager);
    }
    private record Pending(UUID id,String route,String payload,int attempts) {}

    @Scheduled(fixedDelayString="${swiftdrop.events.publish-delay-ms:1000}")
    public void publishDue() {
        var ids=jdbc.query("""
            SELECT event_id FROM courier_event_outbox WHERE published_at IS NULL AND next_attempt_at<=now()
            ORDER BY occurred_at,event_id LIMIT 10
            """,(rs,row)->rs.getObject(1,UUID.class));
        for(var id:ids)transactions.executeWithoutResult(tx->{
            var rows=jdbc.query("""
                SELECT event_id,routing_key,payload,attempts FROM courier_event_outbox
                WHERE event_id=? AND published_at IS NULL AND next_attempt_at<=now() FOR UPDATE SKIP LOCKED
                """,(rs,row)->new Pending(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getInt(4)),id);
            if(rows.isEmpty())return;var pending=rows.getFirst();
            try {
                var properties=new MessageProperties();properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);properties.setMessageId(id.toString());
                var correlation=new CorrelationData(id.toString());
                rabbit.send(CourierRabbitConfig.EXCHANGE,pending.route(),new Message(pending.payload().getBytes(StandardCharsets.UTF_8),properties),correlation);
                var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                if(!confirm.isAck()||correlation.getReturned()!=null)throw new IllegalStateException("Event was not acknowledged and routed to a subscriber");
                jdbc.update("UPDATE courier_event_outbox SET published_at=now(),attempts=attempts+1,last_error=NULL WHERE event_id=?",id);
            } catch(Exception failure) {
                if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
                String reason=failure.getClass().getSimpleName();
                long seconds=Math.min(300,1L<<Math.min(pending.attempts()+1,8));
                jdbc.update("UPDATE courier_event_outbox SET attempts=attempts+1,next_attempt_at=?,last_error=? WHERE event_id=?",
                    Timestamp.from(Instant.now().plusSeconds(seconds)),reason,id);
            }
        });
    }
}
