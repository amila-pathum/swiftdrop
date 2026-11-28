package com.swiftdrop.courier.photo;

import java.time.Instant;
import java.util.Map;

/**
 * Private object storage for delivery photos. Browsers upload and view photos
 * directly through short-lived signed URLs; image bytes never pass through the
 * API except for the one-time content check in {@link DeliveryPhotoService}.
 */
public interface PhotoStorage {
    /** A signed PUT the browser sends the file to, with the headers it must send unchanged. */
    record UploadTicket(String uploadUrl, Map<String,String> headers, Instant expiresAt) {}

    UploadTicket presignUpload(String key, String contentType, long sizeBytes);

    /** The stored bytes, at most {@code maxBytes + 1} of them, or null when the object does not exist. */
    byte[] read(String key, int maxBytes);

    /** A signed GET URL, valid for a few minutes. */
    String viewUrl(String key);

    void delete(String key);
}
