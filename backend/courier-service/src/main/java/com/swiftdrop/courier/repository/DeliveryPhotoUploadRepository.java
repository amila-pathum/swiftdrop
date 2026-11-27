package com.swiftdrop.courier.repository;

import com.swiftdrop.courier.entity.DeliveryPhotoUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface DeliveryPhotoUploadRepository extends JpaRepository<DeliveryPhotoUpload,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from DeliveryPhotoUpload u where u.photoKey=:key")
    Optional<DeliveryPhotoUpload> lockByKey(@Param("key") String key);
    List<DeliveryPhotoUpload> findTop100ByUsedAtIsNullAndCreatedAtBefore(Instant cutoff);
}
