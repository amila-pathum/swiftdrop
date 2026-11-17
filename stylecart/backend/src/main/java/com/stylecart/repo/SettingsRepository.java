package com.stylecart.repo;
import com.stylecart.model.IntegrationSettings;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SettingsRepository extends JpaRepository<IntegrationSettings,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from IntegrationSettings s where s.id=1") Optional<IntegrationSettings> lock();
}
