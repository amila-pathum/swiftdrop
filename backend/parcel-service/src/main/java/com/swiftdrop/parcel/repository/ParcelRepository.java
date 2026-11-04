package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    boolean existsByTrackingNumber(String trackingNumber);
    List<Parcel> findBySenderIdOrderByCreatedAtDesc(UUID senderId);
    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    List<Parcel> findByStatusOrderByCreatedAtAsc(ParcelStatus status);
}
