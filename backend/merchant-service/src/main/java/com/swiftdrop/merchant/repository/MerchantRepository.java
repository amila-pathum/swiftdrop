package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Merchant m where m.id = :id")
    Optional<Merchant> findForReviewById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Merchant> findByRegistrationNumber(String registrationNumber);

    boolean existsByUserId(Long userId);

    boolean existsByRegistrationNumber(String registrationNumber);

    List<Merchant> findByStatus(MerchantStatus status);
}
