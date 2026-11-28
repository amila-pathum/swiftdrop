package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.RouteRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RouteRateRepository extends JpaRepository<RouteRate, UUID> {

    /** The rate between two zones in a version, whichever order they are given in. */
    @Query("""
        select r from RouteRate r
        where r.rateVersion.id = :versionId
          and ((r.zoneA.id = :first and r.zoneB.id = :second)
            or (r.zoneA.id = :second and r.zoneB.id = :first))
        """)
    Optional<RouteRate> findRoute(@Param("versionId") UUID versionId,
                                  @Param("first") UUID first, @Param("second") UUID second);

    /** True when any rate card version, past or current, prices a route to or from the zone. */
    @Query("select count(r) > 0 from RouteRate r where r.zoneA.id = :zoneId or r.zoneB.id = :zoneId")
    boolean existsForZone(@Param("zoneId") UUID zoneId);

    @Query("select count(r) from RouteRate r where r.rateVersion.id = :versionId")
    long countByVersion(@Param("versionId") UUID versionId);
}
