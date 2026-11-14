package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
