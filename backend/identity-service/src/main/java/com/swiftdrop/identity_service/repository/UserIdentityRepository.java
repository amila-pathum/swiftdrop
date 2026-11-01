package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.AuthProvider;
import com.swiftdrop.identity_service.entity.UserIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<UserIdentity> findByProviderAndProviderSubject(
        AuthProvider provider, String providerSubject);
}
