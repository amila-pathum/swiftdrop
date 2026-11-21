package com.swiftdrop.notification.consumer;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.ProcessedEvent;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.repository.ProcessedEventRepository;
import com.swiftdrop.notification.service.NotificationStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ParcelAssignedConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final NotificationStreamService stream = mock(NotificationStreamService.class);
    private ParcelAssignedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ParcelAssignedConsumer(new ObjectMapper(), notifications, processed, stream);
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Same shape dispatch-service publishes (ParcelAssignedEvent). */
    private static byte[] event(String courierUserId, String previousCourierUserId) {
        return ("""
            {
              "eventId": "%s",
              "eventType": "ParcelAssigned",
              "occurredAt": "2026-11-21T10:00:00Z",
              "parcelId": "edbd9e0a-0cfc-4289-a18d-7251a0584ffc",
              "trackingNumber": "SD53007878",
              "courierId": "3",
              "courierUserId": %s,
              "previousCourierId": %s,
              "previousCourierUserId": %s,
              "assignedBy": "70"
            }
            """).formatted(EVENT_ID,
                courierUserId == null ? "null" : "\"" + courierUserId + "\"",
                previousCourierUserId == null ? "null" : "\"2\"",
                previousCourierUserId == null ? "null" : "\"" + previousCourierUserId + "\"")
            .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void assignedCourierGetsInAppNotificationAndLivePush() throws Exception {
        consumer.consume(event("88", null));

        verify(notifications).save(argThat(n -> n.getUserId().equals("88")
                && n.getTitle().equals(ParcelAssignedConsumer.ASSIGNED_TITLE)
                && n.getMessage().startsWith("Parcel SD53007878 has been assigned to you.")
                && n.getTrackingNumber().equals("SD53007878")
                && n.getCreatedAt().equals(Instant.parse("2026-11-21T10:00:00Z"))
                && !n.isRead()));
        verify(notifications, times(1)).save(any(Notification.class));
        verify(stream).broadcastToUser(eq("88"), any(NotificationResponse.class));
        verify(processed).save(argThat((ProcessedEvent p) -> p.getEventId().equals(EVENT_ID)));
    }

    @Test
    void reassignmentAlsoTellsThePreviousCourier() throws Exception {
        consumer.consume(event("88", "77"));

        verify(notifications).save(argThat(n -> n.getUserId().equals("88")
                && n.getTitle().equals(ParcelAssignedConsumer.ASSIGNED_TITLE)));
        verify(notifications).save(argThat(n -> n.getUserId().equals("77")
                && n.getTitle().equals(ParcelAssignedConsumer.REASSIGNED_TITLE)
                && n.getMessage().equals("Parcel SD53007878 has been reassigned to another courier.")));
        verify(stream).broadcastToUser(eq("77"), any(NotificationResponse.class));
    }

    @Test
    void duplicateEventIsIgnored() throws Exception {
        when(processed.existsByEventId(EVENT_ID)).thenReturn(true);

        consumer.consume(event("88", null));

        verifyNoInteractions(notifications, stream);
        verify(processed, never()).save(any());
    }

    @Test
    void eventWithoutCourierUserIdNotifiesNobody() throws Exception {
        consumer.consume(event(null, null));

        verifyNoInteractions(notifications, stream);
        verify(processed).save(any(ProcessedEvent.class));
    }
}
