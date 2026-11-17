package com.stylecart.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="products")
public class Product {
    @Id public String id;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String category;
    @Column(nullable=false) public String description;
    @Column(nullable=false,precision=12,scale=2) public BigDecimal price;
    @Column(nullable=false,precision=8,scale=3) public BigDecimal weight;
    @Column(nullable=false) public String image;
    @Column(nullable=false) public String sizes;
    @Column(nullable=false) public String colors;
    @Column(nullable=false) public boolean featured;
}
