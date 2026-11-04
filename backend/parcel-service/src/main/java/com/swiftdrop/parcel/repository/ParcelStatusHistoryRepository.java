package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {
    List<ParcelStatusHistory> findByParcelIdOrderByChangedAtAsc(UUID parcelId);
}
