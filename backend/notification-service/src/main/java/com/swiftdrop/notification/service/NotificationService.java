package com.swiftdrop.notification.service;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String userId) {
        return getMyNotifications(userId, null);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String userId, String email) {
        List<String> identifiers = Stream.of(userId, email)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (identifiers.isEmpty()) {
            return List.of();
        }
        return notificationRepository.findByUserIdInOrderByCreatedAtDesc(identifiers)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return getUnreadCount(userId, null);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId, String email) {
        List<String> identifiers = Stream.of(userId, email)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (identifiers.isEmpty()) {
            return 0L;
        }
        return notificationRepository.countByUserIdInAndIsReadFalse(identifiers);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID id, String userId) {
        return markAsRead(id, userId, null);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID id, String userId, String email) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        boolean isOwner = (StringUtils.hasText(userId) && userId.equals(notification.getUserId()))
                || (StringUtils.hasText(email) && email.equalsIgnoreCase(notification.getUserId()));

        if (!isOwner) {
            // Requirement: A user cannot access/modify another user's notifications (403 Forbidden)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to notification");
        }

        notification.setRead(true);
        Notification updated = notificationRepository.save(notification);
        return NotificationResponse.from(updated);
    }
}

