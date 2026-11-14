package com.swiftdrop.courier.repository;
import com.swiftdrop.courier.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof,UUID> {
    Optional<DeliveryProof> findByCheckpointId(UUID checkpointId);
    List<DeliveryProof> findByParcelIdAndCourierIdAndCheckpointSyncStatusOrderByRecordedAtAsc(UUID parcelId, Long courierId, SyncStatus status);
    Optional<DeliveryProof> findByIdAndParcelIdAndCourierIdAndCheckpointSyncStatus(UUID id, UUID parcelId, Long courierId, SyncStatus status);
}
