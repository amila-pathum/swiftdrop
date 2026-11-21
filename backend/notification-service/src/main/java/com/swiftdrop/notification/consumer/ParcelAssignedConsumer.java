package com.swiftdrop.notification.consumer;

import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.ProcessedEvent;
import com.swiftdrop.notification.event.ParcelAssignedEvent;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.repository.ProcessedEventRepository;
import com.swiftdrop.notification.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Tells a courier, in the app, that a parcel has been assigned to them. On a
 * reassignment the previous courier is told the parcel is no longer theirs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "swiftdrop.notifications.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class ParcelAssignedConsumer {

    public static final String ASSIGNED_TITLE = "New Delivery Assigned";
    public static final String REASSIGNED_TITLE = "Delivery Reassigned";

    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationStreamService notificationStreamService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.ASSIGNED_QUEUE)
    public void consume(byte[] message) throws Exception {
        ParcelAssignedEvent event = objectMapper.readValue(message, ParcelAssignedEvent.class);
        log.info("Received parcel assigned event: eventId={}, trackingNumber={}, courierUserId={}",
                event.eventId(), event.trackingNumber(), event.courierUserId());

        if (event.eventId() != null && processedEventRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate event ignored: eventId={}", event.eventId());
            return;
        }

        Instant eventTime = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        String tracking = StringUtils.hasText(event.trackingNumber()) ? event.trackingNumber() : "A parcel";

        if (StringUtils.hasText(event.courierUserId())) {
            notify(event.courierUserId(), event.trackingNumber(), ASSIGNED_TITLE,
                    String.format("Parcel %s has been assigned to you. Open your deliveries to see the pickup details.", tracking),
                    eventTime);
        } else {
            log.warn("Parcel assigned event {} has no courierUserId; courier not notified", event.eventId());
        }

        if (StringUtils.hasText(event.previousCourierUserId())
                && !event.previousCourierUserId().equals(event.courierUserId())) {
            notify(event.previousCourierUserId(), event.trackingNumber(), REASSIGNED_TITLE,
                    String.format("Parcel %s has been reassigned to another courier.", tracking),
                    eventTime);
        }

        if (event.eventId() != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(event.eventId())
                    .eventType(StringUtils.hasText(event.eventType()) ? event.eventType() : "parcel.assigned")
                    .processedAt(Instant.now())
                    .build());
        }
    }

    private void notify(String userId, String trackingNumber, String title, String text, Instant at) {
        Notification saved = notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .trackingNumber(trackingNumber)
                .title(title)
                .message(text)
                .isRead(false)
                .createdAt(at)
                .build());
        notificationStreamService.broadcastToUser(userId, NotificationResponse.from(saved));
    }
}
