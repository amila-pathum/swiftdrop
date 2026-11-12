package com.swiftdrop.merchant.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "merchants")
public class Merchant {

    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public Long getUserId() {
        return userId;
    }


    public void setUserId(Long userId) {
        this.userId = userId;
    }


    public String getBusinessName() {
        return businessName;
    }


    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }


    public String getRegistrationNumber() {
        return registrationNumber;
    }


    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }


    public String getContactName() {
        return contactName;
    }


    public void setContactName(String contactName) {
        this.contactName = contactName;
    }


    public String getEmail() {
        return email;
    }


    public void setEmail(String email) {
        this.email = email;
    }


    public String getPhone() {
        return phone;
    }


    public void setPhone(String phone) {
        this.phone = phone;
    }


    public String getAddress() {
        return address;
    }


    public void setAddress(String address) {
        this.address = address;
    }


    public MerchantStatus getStatus() {
        return status;
    }


    public void setStatus(MerchantStatus status) {
        this.status = status;
    }


    public String getRejectionReason() {
        return rejectionReason;
    }


    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }


    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }


    public Long getReviewedBy() {
        return reviewedBy;
    }


    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }


    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }


    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status = MerchantStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    
@PrePersist
protected void onCreate() {
    if (createdAt == null) {
        createdAt = OffsetDateTime.now();
    }
    if (status == null) {
        status = MerchantStatus.PENDING;
    }
}

}
