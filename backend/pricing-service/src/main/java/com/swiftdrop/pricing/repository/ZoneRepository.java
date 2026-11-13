
package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByCityIgnoreCase(String city);

    List<Zone> findByActiveTrue();

    boolean existsByCityIgnoreCase(String city);
}
