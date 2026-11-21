package com.swiftdrop.courier.application;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierApplicationRepository extends JpaRepository<CourierApplication, Long> {
    Optional<CourierApplication> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    List<CourierApplication> findAllByOrderByCreatedAtDesc();
    List<CourierApplication> findByStatusOrderByCreatedAtDesc(ApplicationStatus status);

    /** Serializes reviews so two administrators cannot decide the same application at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CourierApplication a where a.id = :id")
    Optional<CourierApplication> findForReviewById(@Param("id") Long id);
}
