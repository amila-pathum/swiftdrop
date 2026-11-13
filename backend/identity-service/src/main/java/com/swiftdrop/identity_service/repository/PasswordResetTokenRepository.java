package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Marks an unused, unexpired token as used in a single statement, so two
     * concurrent resets cannot both succeed. Returns the rows updated (0 or 1).
     */
    @Modifying
    @Query("""
        UPDATE PasswordResetToken t SET t.usedAt = :now
        WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.expiresAt > :now
        """)
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** A user has at most one usable token: issuing a new one removes the others. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") long userId);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
