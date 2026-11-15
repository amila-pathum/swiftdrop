package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.UnreadCountResponse;
import com.swiftdrop.notification.service.NotificationService;
import com.swiftdrop.notification.service.NotificationStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationStreamService notificationStreamService = mock(NotificationStreamService.class);
    private final NotificationController controller = new NotificationController(notificationService, notificationStreamService);

    private Jwt createMockJwt(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(subject + "@example.com");
        return jwt;
    }

    @Test
    void listMyNotificationsReturnsListForAuthenticatedUser() {
        Jwt jwt = createMockJwt("user-42");
        NotificationResponse res = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .userId("user-42")
                .trackingNumber("SD-123")
                .title("Status")
                .message("In transit")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getMyNotifications("user-42", "user-42@example.com")).thenReturn(List.of(res));

        List<NotificationResponse> result = controller.listMyNotifications(jwt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trackingNumber()).isEqualTo("SD-123");
        verify(notificationService).getMyNotifications("user-42", "user-42@example.com");
    }

    @Test
    void getUnreadCountReturnsCountForAuthenticatedUser() {
        Jwt jwt = createMockJwt("user-42");
        when(notificationService.getUnreadCount("user-42", "user-42@example.com")).thenReturn(3L);

        UnreadCountResponse response = controller.getUnreadCount(jwt);

        assertThat(response.unreadCount()).isEqualTo(3L);
        verify(notificationService).getUnreadCount("user-42", "user-42@example.com");
    }

    @Test
    void markAsReadDelegatesToService() {
        UUID id = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-42");
        NotificationResponse res = NotificationResponse.builder()
                .id(id)
                .userId("user-42")
                .trackingNumber("SD-123")
                .title("Status")
                .message("In transit")
                .isRead(true)
                .createdAt(Instant.now())
                .build();

        when(notificationService.markAsRead(id, "user-42", "user-42@example.com")).thenReturn(res);

        NotificationResponse response = controller.markAsRead(id, jwt);

        assertThat(response.isRead()).isTrue();
        verify(notificationService).markAsRead(id, "user-42", "user-42@example.com");
    }

    @Test
    void streamUserNotificationsReturnsEmitter() {
        Jwt jwt = createMockJwt("user-42");
        SseEmitter emitter = new SseEmitter();
        when(notificationStreamService.subscribeUser("user-42", "user-42@example.com")).thenReturn(emitter);

        SseEmitter result = controller.streamUserNotifications(jwt);

        assertThat(result).isSameAs(emitter);
        verify(notificationStreamService).subscribeUser("user-42", "user-42@example.com");
    }

    @Test
    void streamParcelStatusReturnsEmitter() {
        SseEmitter emitter = new SseEmitter();
        when(notificationStreamService.subscribeParcel("SD-123")).thenReturn(emitter);

        SseEmitter result = controller.streamParcelStatus("SD-123");

        assertThat(result).isSameAs(emitter);
        verify(notificationStreamService).subscribeParcel("SD-123");
    }
}

