package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marks an unused, unexpired token as used in a single statement, so two
     * concurrent refreshes with one token cannot both succeed. Returns 0 or 1.
     */
    @Modifying
    @Query("""
        UPDATE RefreshToken t SET t.usedAt = :now
        WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.expiresAt > :now
        """)
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") String familyId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") long userId);

    /** Removes a user's tokens issued before their token version last changed. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId AND t.tokenVersion <> :version")
    int deleteStale(@Param("userId") long userId, @Param("version") int version);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
