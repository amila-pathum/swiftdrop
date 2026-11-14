package com.swiftdrop.parcel.client;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import java.math.BigDecimal;

public interface PricingClient {
    BigDecimal quote(CreateParcelRequest request, String accessToken);
}
