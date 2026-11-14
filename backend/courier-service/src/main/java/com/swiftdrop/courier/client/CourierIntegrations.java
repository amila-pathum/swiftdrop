package com.swiftdrop.courier.client;
import com.swiftdrop.courier.entity.DeliveryStatus;
import java.time.*;
import java.util.*;

/** Proposed contracts: see README; remote services remain authoritative. */
public final class CourierIntegrations {
    private CourierIntegrations() {}
    public record IdentityAccount(Long id, String role, String status) {}
    public record Assignment(UUID id, UUID parcelId, Long courierId, int attemptNo,
                             boolean active, long version, LocalDate deliveryDate, Instant assignedAt) {}
    public record ParcelDetails(UUID id, String trackingNumber, String status, String pickupAddress,
                                String deliveryAddress, String receiverName, String receiverPhone) {}
    public record StatusUpdate(DeliveryStatus status, String note, Long actorId, Long courierId,
                               UUID assignmentId, long assignmentVersion, int attemptNo, UUID submissionId) {}
    public interface IdentityClient { IdentityAccount account(Long userId, String token); }
    public interface DispatchClient { List<Assignment> assignments(Long courierId, String token); }
    public interface ParcelClient {
        ParcelDetails parcel(UUID parcelId, String token);
        void update(UUID parcelId, StatusUpdate update, String token);
    }
    public static class RemoteFailure extends RuntimeException {
        public final boolean rejected;
        public RemoteFailure(String service, boolean rejected) {
            super(service+" could not complete the request. Verify the integration contract and service availability.");
            this.rejected=rejected;
        }
    }
}
