package com.swiftdrop.notification.repository;

import com.swiftdrop.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdInOrderByCreatedAtDesc(Collection<String> userIds);

    long countByUserIdAndIsReadFalse(String userId);

    long countByUserIdInAndIsReadFalse(Collection<String> userIds);

    Optional<Notification> findByIdAndUserId(UUID id, String userId);
}

