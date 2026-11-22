package com.swiftdrop.courier.application;

/**
 * Makes sure the applicant's Identity account is an ACTIVE courier, activating a
 * PENDING_APPROVAL one with the approving administrator's own token. Identity
 * stays the owner of account status. Safe to repeat.
 */
public interface CourierAccountActivation {
    void ensureActive(Long userId, String adminToken);
}
