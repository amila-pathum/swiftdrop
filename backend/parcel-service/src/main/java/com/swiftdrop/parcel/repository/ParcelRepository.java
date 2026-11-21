package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    // PostgreSQL handles concurrent collisions without aborting the surrounding
    // transaction (which may also own a merchant idempotency reservation).
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO parcels (id, tracking_number, sender_id, receiver_name, receiver_phone,
            pickup_address, delivery_address, weight, size, parcel_type, charge, status,
            created_by, merchant_id, merchant_order_reference, created_at, updated_at)
        VALUES (:#{#p.id}, :#{#p.trackingNumber}, :#{#p.senderId}, :#{#p.receiverName},
            :#{#p.receiverPhone}, :#{#p.pickupAddress}, :#{#p.deliveryAddress}, :#{#p.weight},
            :#{#p.size}, :#{#p.parcelType}, :#{#p.charge}, 'CREATED', :#{#p.createdBy},
            :#{#p.merchantId}, :#{#p.merchantOrderReference}, :#{#p.createdAt}, :#{#p.updatedAt})
        ON CONFLICT (tracking_number) DO NOTHING
        """, nativeQuery = true)
    int insertIfTrackingNumberAvailable(@Param("p") Parcel parcel);

    // Serialize status changes so each history row records the actual previous status.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> findForStatusUpdateById(@Param("id") UUID id);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    boolean existsByTrackingNumber(String trackingNumber);
    List<Parcel> findBySenderIdOrderByCreatedAtDesc(Long senderId);
    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    List<Parcel> findByStatusOrderByCreatedAtAsc(ParcelStatus status);

    Page<Parcel> findBySenderId(Long senderId, Pageable pageable);
    Page<Parcel> findBySenderIdAndStatus(Long senderId, ParcelStatus status, Pageable pageable);

    /** One row per status the sender has parcels in: [ParcelStatus, Long count]. */
    @Query("select p.status, count(p) from Parcel p where p.senderId = :senderId group by p.status")
    List<Object[]> countBySenderIdGroupedByStatus(@Param("senderId") Long senderId);
    Page<Parcel> findByMerchantId(Long merchantId, Pageable pageable);
    Page<Parcel> findByStatus(ParcelStatus status, Pageable pageable);
}
