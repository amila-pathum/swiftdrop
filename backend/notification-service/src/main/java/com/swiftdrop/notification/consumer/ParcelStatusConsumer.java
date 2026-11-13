package com.swiftdrop.notification.consumer;

import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.ProcessedEvent;
import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.repository.ProcessedEventRepository;
import com.swiftdrop.notification.service.EmailNotificationService;
import com.swiftdrop.notification.service.NotificationStreamService;
import com.swiftdrop.notification.service.SmsNotificationService;
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "swiftdrop.notifications.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class ParcelStatusConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final NotificationStreamService notificationStreamService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consume(byte[] message) throws Exception {
        ParcelStatusChangedEvent event = objectMapper.readValue(
                message,
                ParcelStatusChangedEvent.class);

        log.info(
                "Received parcel status event: eventId={}, trackingNumber={}, oldStatus={}, newStatus={}",
                event.eventId(),
                event.trackingNumber(),
                event.oldStatus(),
                event.newStatus());

        // 1. Duplicate events check : ignore repeats
        if (event.eventId() != null && processedEventRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate event ignored: eventId={}, trackingNumber={}", event.eventId(), event.trackingNumber());
            return;
        }

        Instant eventTime = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        // 2. Customer notification : Save in-app notification, send email and mock SMS
        if (StringUtils.hasText(event.customerId())) {
            String messageText;
            if (StringUtils.hasText(event.oldStatus())) {
                messageText = String.format("Parcel %s status updated from %s to %s",
                        event.trackingNumber(), event.oldStatus(), event.newStatus());
            } else {
                messageText = String.format("Parcel %s status is now %s",
                        event.trackingNumber(), event.newStatus());
            }

            Notification notification = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(event.customerId())
                    .trackingNumber(event.trackingNumber())
                    .title("Parcel Status Update")
                    .message(messageText)
                    .isRead(false)
                    .createdAt(eventTime)
                    .build();

            Notification saved = notificationRepository.save(notification);

            // Send email & mock SMS
            emailNotificationService.sendParcelUpdateEmail(event);
            smsNotificationService.sendParcelUpdateSms(event);

            // Broadcast real-time update to customer's active browser sessions (Task 6)
            notificationStreamService.broadcastToUser(event.customerId(), NotificationResponse.from(saved));
        }

        // 3. Broadcast real-time update to public tracking stream (Task 6)
        notificationStreamService.broadcastParcelStatus(event.trackingNumber(), event.newStatus(), eventTime);

        // 4. Mark event ID as processed for idempotency
        if (event.eventId() != null) {
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.eventId())
                    .eventType(StringUtils.hasText(event.eventType()) ? event.eventType() : "parcel.status-changed")
                    .processedAt(Instant.now())
                    .build();
            processedEventRepository.save(processedEvent);
        }
    }
}
