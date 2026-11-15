package com.stylecart.repo;
import com.stylecart.model.ShopUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<ShopUser,Long> { Optional<ShopUser> findByEmailIgnoreCase(String email); }
