package com.swiftdrop.notification.webhook.retry;

import java.util.Optional;

/** Translates the publisher's Identity user ID to the settings owner's Merchant profile ID. */
public interface MerchantProfileLookup {
    Optional<String> profileForUser(String userId);
}
