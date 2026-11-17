package com.stylecart.repo;
import com.stylecart.model.WebhookAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AttemptRepository extends JpaRepository<WebhookAttempt,Long> { List<WebhookAttempt> findTop100ByOrderByReceivedAtDesc(); }
