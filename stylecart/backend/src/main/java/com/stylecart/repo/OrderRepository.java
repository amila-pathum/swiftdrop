package com.stylecart.repo;
import com.stylecart.model.StoreOrder;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface OrderRepository extends JpaRepository<StoreOrder,UUID> {
    List<StoreOrder> findTop100ByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<StoreOrder> findTop100ByOrderByCreatedAtDesc();
    Optional<StoreOrder> findByReference(String reference);
    Optional<StoreOrder> findByTrackingNumber(String number);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from StoreOrder o where o.id=:id")
    Optional<StoreOrder> lock(@Param("id") UUID id);
}
