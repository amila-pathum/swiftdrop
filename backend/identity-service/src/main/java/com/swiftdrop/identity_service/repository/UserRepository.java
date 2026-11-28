package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<User> findByRoleAndStatusOrderByIdAsc(Role role, UserStatus status);
}
