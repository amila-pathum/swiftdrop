package com.swiftdrop.merchant.merchantapi.security;

import java.util.Optional;

public interface MerchantApiKeyLookup {

    Optional<MerchantKeyDetails> findByApiKey(String apiKey);

}