package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findByActiveTrueOrderByNameAsc();

    List<Zone> findAllByOrderByNameAsc();

    Optional<Zone> findByCodeIgnoreCase(String code);

    /** The zone covering a locality, active or not; matching ignores case. */
    @Query("select z from Zone z join z.localities l where lower(l) = lower(:locality)")
    Optional<Zone> findByLocality(@Param("locality") String locality);
}
