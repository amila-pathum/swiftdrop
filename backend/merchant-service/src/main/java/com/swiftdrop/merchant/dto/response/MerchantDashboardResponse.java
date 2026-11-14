package com.swiftdrop.merchant.dto.response;

import java.util.List;
import java.util.Map;

public record MerchantDashboardResponse(
        Long merchantId,
        String businessName,
        String status,
        long totalOrders,
        long pendingOrders,
        long inTransitOrders,
        long deliveredOrders,
        Map<String, Long> ordersByStatus,
        List<MerchantDashboardOrderDto> recentOrders
) {
}

