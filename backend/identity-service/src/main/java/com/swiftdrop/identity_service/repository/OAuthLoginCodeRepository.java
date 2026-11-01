package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.OAuthLoginCode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthLoginCodeRepository extends JpaRepository<OAuthLoginCode, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<OAuthLoginCode> findByCodeHash(String codeHash);

    /**
     * Marks an unused, unexpired code as used in a single statement, so two
     * concurrent exchanges cannot both succeed. Returns the rows updated (0 or 1).
     */
    @Modifying
    @Query("""
        UPDATE OAuthLoginCode c SET c.usedAt = :now
        WHERE c.codeHash = :codeHash AND c.usedAt IS NULL AND c.expiresAt > :now
        """)
    int markUsed(@Param("codeHash") String codeHash, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM OAuthLoginCode c WHERE c.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
