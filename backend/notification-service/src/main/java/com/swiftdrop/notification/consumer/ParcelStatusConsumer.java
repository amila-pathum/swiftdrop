package com.swiftdrop.notification.consumer;

import com.swiftdrop.notification.client.IdentityServiceClient;
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
import java.util.List;
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
    private final IdentityServiceClient identityServiceClient;

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
            String title = resolveNotificationTitle(event.newStatus());
            String messageText = resolveNotificationDescription(event.newStatus(), event.trackingNumber());

            Notification notification = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(event.customerId())
                    .trackingNumber(event.trackingNumber())
                    .title(title)
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

        // 3. Dispatcher notification : a new parcel is waiting to be assigned a courier
        if ("CREATED".equalsIgnoreCase(event.newStatus())) {
            notifyDispatchers(event, eventTime);
        }

        // 4. Broadcast real-time update to public tracking stream (Task 6)
        notificationStreamService.broadcastParcelStatus(event.trackingNumber(), event.newStatus(), eventTime);

        // 5. Mark event ID as processed for idempotency
        if (event.eventId() != null) {
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.eventId())
                    .eventType(StringUtils.hasText(event.eventType()) ? event.eventType() : "parcel.status-changed")
                    .processedAt(Instant.now())
                    .build();
            processedEventRepository.save(processedEvent);
        }
    }

    /**
     * One in-app notification (and live push) per ACTIVE dispatcher. No email or
     * SMS: dispatchers work in the app. If identity-service is unreachable the
     * customer notification still goes out and this step is skipped.
     */
    private void notifyDispatchers(ParcelStatusChangedEvent event, Instant eventTime) {
        List<String> dispatchers = identityServiceClient.findActiveUserIdsByRole("DISPATCHER");
        if (dispatchers.isEmpty()) {
            log.warn("No active dispatchers found for new parcel {}", event.trackingNumber());
            return;
        }
        String tracking = StringUtils.hasText(event.trackingNumber()) ? event.trackingNumber() : "A new parcel";
        for (String dispatcherId : dispatchers) {
            Notification saved = notificationRepository.save(Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(dispatcherId)
                    .trackingNumber(event.trackingNumber())
                    .title(DISPATCHER_NEW_PARCEL_TITLE)
                    .message(String.format("Parcel %s has been created and is waiting for a courier.", tracking))
                    .isRead(false)
                    .createdAt(eventTime)
                    .build());
            notificationStreamService.broadcastToUser(dispatcherId, NotificationResponse.from(saved));
        }
    }

    public static final String DISPATCHER_NEW_PARCEL_TITLE = "New Parcel to Assign";

    public static String resolveNotificationTitle(String newStatus) {
        if (!StringUtils.hasText(newStatus)) {
            return "Parcel Status Update";
        }
        return switch (newStatus.trim().toUpperCase()) {
            case "CREATED", "PENDING" -> "Parcel Registered";
            case "PICKED_UP" -> "Parcel Picked Up";
            case "IN_TRANSIT" -> "Parcel In Transit";
            case "OUT_FOR_DELIVERY" -> "Parcel Out for Delivery";
            case "DELIVERED" -> "Parcel Delivered";
            case "CANCELLED" -> "Parcel Cancelled";
            case "RETURNED" -> "Parcel Returned";
            case "FAILED", "FAILED_DELIVERY" -> "Delivery Attempt Failed";
            default -> "Parcel " + EmailNotificationService.formatFriendlyStatus(newStatus);
        };
    }

    public static String resolveNotificationDescription(String newStatus, String trackingNumber) {
        String tracking = StringUtils.hasText(trackingNumber) ? trackingNumber : "your parcel";
        if (!StringUtils.hasText(newStatus)) {
            return String.format("Your parcel %s status has been updated.", tracking);
        }
        return switch (newStatus.trim().toUpperCase()) {
            case "CREATED", "PENDING" ->
                String.format("Your parcel %s has been registered and is being processed.", tracking);
            case "PICKED_UP" ->
                String.format("Your parcel %s has been picked up by the courier.", tracking);
            case "IN_TRANSIT" ->
                String.format("Your parcel %s is on the way to the delivery location.", tracking);
            case "OUT_FOR_DELIVERY" ->
                String.format("Your parcel %s is out for delivery and will arrive soon.", tracking);
            case "DELIVERED" ->
                String.format("Your parcel %s has been successfully delivered.", tracking);
            case "CANCELLED" ->
                String.format("Your delivery for parcel %s has been cancelled.", tracking);
            case "RETURNED" ->
                String.format("Your parcel %s is being returned to the sender.", tracking);
            case "FAILED", "FAILED_DELIVERY" ->
                String.format("Delivery attempt for parcel %s was unsuccessful.", tracking);
            default ->
                String.format("Your parcel %s is now %s.", tracking, EmailNotificationService.formatFriendlyStatus(newStatus));
        };
    }
}
