package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.RateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RateVersionRepository extends JpaRepository<RateVersion, UUID> {

    /** The published version that quotes use. */
    Optional<RateVersion> findTopByOrderByVersionDesc();

    List<RateVersion> findAllByOrderByVersionDesc();
}
