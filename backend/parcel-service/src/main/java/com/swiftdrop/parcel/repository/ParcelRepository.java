package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    // Serialize status changes so each history row records the actual previous status.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> findForStatusUpdateById(@Param("id") UUID id);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    boolean existsByTrackingNumber(String trackingNumber);
    List<Parcel> findBySenderIdOrderByCreatedAtDesc(UUID senderId);
    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    List<Parcel> findByStatusOrderByCreatedAtAsc(ParcelStatus status);
}
