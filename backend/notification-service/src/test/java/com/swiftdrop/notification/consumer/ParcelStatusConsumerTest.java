package com.swiftdrop.notification.consumer;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.ProcessedEvent;
import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.repository.ProcessedEventRepository;
import com.swiftdrop.notification.service.EmailNotificationService;
import com.swiftdrop.notification.service.NotificationStreamService;
import com.swiftdrop.notification.service.SmsNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ParcelStatusConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final EmailNotificationService emailNotificationService = mock(EmailNotificationService.class);
    private final SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
    private final NotificationStreamService notificationStreamService = mock(NotificationStreamService.class);

    private ParcelStatusConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ParcelStatusConsumer(
                objectMapper,
                notificationRepository,
                processedEventRepository,
                emailNotificationService,
                smsNotificationService,
                notificationStreamService
        );
    }

    private static final String EVENT_JSON = """
            {
              "eventId": "22222222-2222-4222-8222-222222222222",
              "eventType": "ParcelStatusChanged",
              "occurredAt": "2026-11-13T10:00:00Z",
              "trackingNumber": "SD-999",
              "parcelId": "parcel-999",
              "merchantId": "merchant-1",
              "merchantOrderReference": "REF-1",
              "customerId": "cust-42",
              "courierId": "courier-1",
              "oldStatus": "PICKED_UP",
              "newStatus": "IN_TRANSIT"
            }
            """;

    @Test
    void consumesEventAndDispatchesNotifications() throws Exception {
        UUID eventId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.consume(EVENT_JSON.getBytes(StandardCharsets.UTF_8));

        // 1. Verify notification persisted
        verify(notificationRepository).save(argThat(n ->
                n.getUserId().equals("cust-42") &&
                n.getTrackingNumber().equals("SD-999") &&
                !n.isRead()
        ));

        // 2. Verify email & SMS sent
        verify(emailNotificationService).sendParcelUpdateEmail(any(ParcelStatusChangedEvent.class));
        verify(smsNotificationService).sendParcelUpdateSms(any(ParcelStatusChangedEvent.class));

        // 3. Verify user stream broadcast
        verify(notificationStreamService).broadcastToUser(eq("cust-42"), any(NotificationResponse.class));

        // 4. Verify public parcel stream broadcast
        verify(notificationStreamService).broadcastParcelStatus(eq("SD-999"), eq("IN_TRANSIT"), any(Instant.class));

        // 5. Verify processed event recorded
        verify(processedEventRepository).save(argThat(pe -> pe.getEventId().equals(eventId)));
    }

    @Test
    void ignoresDuplicateEvents() throws Exception {
        UUID eventId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        consumer.consume(EVENT_JSON.getBytes(StandardCharsets.UTF_8));

        verify(notificationRepository, never()).save(any());
        verify(emailNotificationService, never()).sendParcelUpdateEmail(any());
        verify(smsNotificationService, never()).sendParcelUpdateSms(any());
        verify(notificationStreamService, never()).broadcastToUser(any(), any());
        verify(notificationStreamService, never()).broadcastParcelStatus(any(), any(), any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void eventWithoutCustomerIdBroadcastsPublicStatusOnly() throws Exception {
        String eventNoCustomer = """
                {
                  "eventId": "33333333-3333-4333-8333-333333333333",
                  "eventType": "ParcelStatusChanged",
                  "occurredAt": "2026-11-13T11:00:00Z",
                  "trackingNumber": "SD-888",
                  "parcelId": "parcel-888",
                  "merchantId": "merchant-2",
                  "merchantOrderReference": "REF-2",
                  "customerId": null,
                  "courierId": null,
                  "oldStatus": "CREATED",
                  "newStatus": "PICKED_UP"
                }
                """;
        UUID eventId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        consumer.consume(eventNoCustomer.getBytes(StandardCharsets.UTF_8));

        // No customer notification
        verify(notificationRepository, never()).save(any());
        verify(emailNotificationService, never()).sendParcelUpdateEmail(any());
        verify(smsNotificationService, never()).sendParcelUpdateSms(any());
        verify(notificationStreamService, never()).broadcastToUser(any(), any());

        // But public parcel status is still broadcast
        verify(notificationStreamService).broadcastParcelStatus(eq("SD-888"), eq("PICKED_UP"), any(Instant.class));

        // Processed event recorded
        verify(processedEventRepository).save(argThat(pe -> pe.getEventId().equals(eventId)));
    }
}

