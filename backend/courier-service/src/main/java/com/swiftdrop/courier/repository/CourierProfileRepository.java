package com.swiftdrop.courier.repository;
import com.swiftdrop.courier.entity.CourierProfile;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface CourierProfileRepository extends JpaRepository<CourierProfile,Long>, JpaSpecificationExecutor<CourierProfile> {
    Optional<CourierProfile> findByUserId(Long userId);
    Optional<CourierProfile> findByDispatchCourierId(java.util.UUID dispatchCourierId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CourierProfile p where p.id=:id")
    Optional<CourierProfile> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
