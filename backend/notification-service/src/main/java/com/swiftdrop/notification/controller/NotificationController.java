package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.UnreadCountResponse;
import com.swiftdrop.notification.service.NotificationService;
import com.swiftdrop.notification.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    /**
     * Lists all notifications for the authenticated user (sorted newest first).
     */
    @GetMapping
    public List<NotificationResponse> listMyNotifications(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.getMyNotifications(jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    /**
     * Gets the count of unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(notificationService.getUnreadCount(jwt.getSubject(), jwt.getClaimAsString("email")));
    }

    /**
     * Marks a specific notification as read.
     * Enforces user isolation (returns 403 Forbidden if the notification belongs to another user).
     */
    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return notificationService.markAsRead(id, jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    /**
     * Server-Sent Events stream for the authenticated user's real-time notifications.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUserNotifications(@AuthenticationPrincipal Jwt jwt) {
        return notificationStreamService.subscribeUser(jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    /**
     * Public Server-Sent Events stream for real-time tracking of a specific parcel.
     * Sends only status and time updates without personal customer data.
     */
    @GetMapping(value = "/parcels/{trackingNumber}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamParcelStatus(@PathVariable String trackingNumber) {
        return notificationStreamService.subscribeParcel(trackingNumber);
    }
}

