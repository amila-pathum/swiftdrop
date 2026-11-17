package com.stylecart.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="store_order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",nullable=false) public StoreOrder order;
    @Column(name="product_id",nullable=false) public String productId;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String image;
    @Column(nullable=false) public String size;
    @Column(nullable=false) public String color;
    @Column(nullable=false) public int quantity;
    @Column(name="unit_price",nullable=false,precision=12,scale=2) public BigDecimal unitPrice;
    @Column(nullable=false,precision=8,scale=3) public BigDecimal weight;
}
