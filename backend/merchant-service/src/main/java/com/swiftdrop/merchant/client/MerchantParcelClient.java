package com.swiftdrop.merchant.client;

import java.util.List;

public interface MerchantParcelClient {

    List<MerchantParcelDto> getParcelsByMerchant(Long userId, Long merchantId);

    List<MerchantParcelHistoryDto> getParcelHistory(String parcelId);
}

