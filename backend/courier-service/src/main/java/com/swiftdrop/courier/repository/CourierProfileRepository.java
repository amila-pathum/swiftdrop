package com.swiftdrop.courier.repository;
import com.swiftdrop.courier.entity.CourierProfile;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface CourierProfileRepository extends JpaRepository<CourierProfile,Long>, JpaSpecificationExecutor<CourierProfile> {
    Optional<CourierProfile> findByUserId(Long userId);
}
