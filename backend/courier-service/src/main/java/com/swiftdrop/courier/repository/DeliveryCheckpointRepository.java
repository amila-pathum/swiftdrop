package com.swiftdrop.courier.repository;
import com.swiftdrop.courier.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface DeliveryCheckpointRepository extends JpaRepository<DeliveryCheckpoint,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeliveryCheckpoint c where c.id=:id")
    Optional<DeliveryCheckpoint> lockById(@Param("id") UUID id);
    List<DeliveryCheckpoint> findByParcelIdAndCourierIdAndSyncStatusOrderByRecordedAtAsc(UUID parcelId, Long courierId, SyncStatus syncStatus);
}
