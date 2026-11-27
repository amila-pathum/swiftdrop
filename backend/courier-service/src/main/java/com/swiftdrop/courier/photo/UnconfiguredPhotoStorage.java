package com.swiftdrop.courier.photo;

import com.swiftdrop.courier.exception.CourierException;
import org.springframework.http.HttpStatus;

/** Used when COURIER_PHOTO_BUCKET is not set: updates without a photo still work. */
public class UnconfiguredPhotoStorage implements PhotoStorage {
    private static CourierException unavailable() {
        return new CourierException(HttpStatus.SERVICE_UNAVAILABLE,"Photo uploads are not configured on this server.");
    }
    @Override public UploadTicket presignUpload(String key,String contentType,long sizeBytes){throw unavailable();}
    @Override public byte[] read(String key,int maxBytes){throw unavailable();}
    @Override public String viewUrl(String key){return null;}
    @Override public void delete(String key){/* Nothing was stored. */}
}
