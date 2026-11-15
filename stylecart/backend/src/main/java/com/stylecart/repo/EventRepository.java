package com.stylecart.repo;
import com.stylecart.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EventRepository extends JpaRepository<WebhookEvent,UUID> {
    List<WebhookEvent> findByOrderIdOrderByOccurredAtAsc(UUID id);
    List<WebhookEvent> findByTrackingNumberOrderByOccurredAtAsc(String tracking);
    List<WebhookEvent> findTop100ByOrderByReceivedAtDesc();
}
