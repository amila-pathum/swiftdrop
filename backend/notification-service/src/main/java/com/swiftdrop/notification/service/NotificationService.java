package com.swiftdrop.notification.service;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID id, String userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            // Requirement: A user cannot access/modify another user's notifications (403 Forbidden)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to notification");
        }

        notification.setRead(true);
        Notification updated = notificationRepository.save(notification);
        return NotificationResponse.from(updated);
    }
}

