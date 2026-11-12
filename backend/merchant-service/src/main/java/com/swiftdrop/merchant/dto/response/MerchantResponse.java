package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.*;
import java.time.OffsetDateTime;

public record MerchantResponse(
    Long id,
    String businessName,
    String registrationNumber,
    String contactName,
    String email,
    String phone,
    String address,
    MerchantStatus status,
    String rejectionReason,
    OffsetDateTime createdAt
) {
    public static MerchantResponse from(Merchant m) {
        return new MerchantResponse(
            m.getId(),
            m.getBusinessName(),
            m.getRegistrationNumber(),
            m.getContactName(),
            m.getEmail(),
            m.getPhone(),
            m.getAddress(),
            m.getStatus(),
            m.getRejectionReason(),
            m.getCreatedAt()
        );
    }
}