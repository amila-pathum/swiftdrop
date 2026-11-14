package com.swiftdrop.courier.repository;

import com.swiftdrop.courier.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> { }
