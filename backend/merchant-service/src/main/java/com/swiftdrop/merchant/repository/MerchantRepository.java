package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByUserId(Long userId);

    Optional<Merchant> findByRegistrationNumber(String registrationNumber);

    boolean existsByUserId(Long userId);

    boolean existsByRegistrationNumber(String registrationNumber);

    List<Merchant> findByStatus(MerchantStatus status);
}
