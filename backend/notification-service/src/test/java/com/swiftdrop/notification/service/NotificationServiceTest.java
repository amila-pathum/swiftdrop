package com.swiftdrop.notification.service;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationService notificationService = new NotificationService(notificationRepository);

    @Test
    void getMyNotificationsReturnsUserNotifications() {
        String userId = "user-123";
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .trackingNumber("SD-100")
                .title("Parcel Update")
                .message("Parcel is on the way")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByUserIdInOrderByCreatedAtDesc(List.of(userId)))
                .thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.getMyNotifications(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).trackingNumber()).isEqualTo("SD-100");
        assertThat(responses.get(0).userId()).isEqualTo(userId);
    }

    @Test
    void getUnreadCountReturnsCount() {
        String userId = "user-123";
        when(notificationRepository.countByUserIdInAndIsReadFalse(List.of(userId))).thenReturn(5L);

        long count = notificationService.getUnreadCount(userId);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void markAsReadUpdatesReadStatus() {
        UUID id = UUID.randomUUID();
        String userId = "user-123";
        Notification notification = Notification.builder()
                .id(id)
                .userId(userId)
                .trackingNumber("SD-100")
                .title("Update")
                .message("Message")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(id, userId);

        assertThat(response.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsReadThrowsForbiddenWhenNotificationBelongsToAnotherUser() {
        UUID id = UUID.randomUUID();
        String ownerUserId = "user-owner";
        String attackerUserId = "user-attacker";

        Notification notification = Notification.builder()
                .id(id)
                .userId(ownerUserId)
                .trackingNumber("SD-100")
                .title("Update")
                .message("Message")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(id, attackerUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadThrowsNotFoundWhenNotificationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(id, "user-123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}

