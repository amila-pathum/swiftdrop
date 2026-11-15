package com.stylecart.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="shop_users")
public class ShopUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,length=100) public String name;
    @Column(nullable=false,unique=true,length=255) public String email;
    @Column(name="password_hash",nullable=false,length=255) public String passwordHash;
    @Column(nullable=false,length=20) public String role;
    @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
}
