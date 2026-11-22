package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.entity.ParcelStatus;
import java.util.Map;

/**
 * How many parcels the authenticated customer has in each status. Every status
 * is present, with zero when the customer has none in it.
 */
public record ParcelSummaryResponse(long total, Map<ParcelStatus, Long> byStatus) {}
