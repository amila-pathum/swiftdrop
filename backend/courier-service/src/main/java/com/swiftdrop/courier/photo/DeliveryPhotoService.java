package com.swiftdrop.courier.photo;

import com.swiftdrop.courier.entity.DeliveryPhotoUpload;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.DeliveryPhotoUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Optional delivery photos. A courier asks for an upload ticket, sends the file
 * straight to storage, then names the returned key in a status update; the key
 * is checked (owner, parcel, unused, real image) before the update is accepted.
 */
@Service
public class DeliveryPhotoService {
    public static final int MAX_BYTES=5*1024*1024;
    static final Duration UNUSED_LIFETIME=Duration.ofHours(24);
    private static final Map<String,String> EXTENSIONS=Map.of("image/jpeg","jpg","image/png","png");
    private static final Logger log=LoggerFactory.getLogger(DeliveryPhotoService.class);

    private final PhotoStorage storage;
    private final DeliveryPhotoUploadRepository uploads;

    public DeliveryPhotoService(PhotoStorage storage,DeliveryPhotoUploadRepository uploads) {
        this.storage=storage;this.uploads=uploads;
    }

    public record Ticket(String photoKey,String uploadUrl,Map<String,String> headers,Instant expiresAt) {}

    public Ticket issue(Long courierId,UUID parcelId,String contentType,long sizeBytes) {
        String type=contentType==null?"":contentType.trim().toLowerCase(Locale.ROOT);
        String extension=EXTENSIONS.get(type);
        if(extension==null)throw CourierException.invalid("Only JPG and PNG photos are accepted.");
        if(sizeBytes<=0)throw CourierException.invalid("The photo is empty.");
        if(sizeBytes>MAX_BYTES)throw new CourierException(HttpStatus.PAYLOAD_TOO_LARGE,"Photos must be at most 5 MB.");
        var upload=new DeliveryPhotoUpload();
        upload.photoKey="proofs/"+parcelId+"/"+UUID.randomUUID()+"."+extension;
        upload.courierId=courierId;upload.parcelId=parcelId;upload.contentType=type;upload.sizeBytes=sizeBytes;
        upload.createdAt=Instant.now();
        var signed=storage.presignUpload(upload.photoKey,type,sizeBytes);
        uploads.save(upload);
        return new Ticket(upload.photoKey,signed.uploadUrl(),signed.headers(),signed.expiresAt());
    }

    /**
     * Confirms the key was issued to this courier for this parcel, has not been
     * attached to an update, and holds a real image of the declared type. A bad
     * file is deleted so it cannot be retried.
     */
    public void verify(Long courierId,UUID parcelId,String photoKey) {
        var upload=uploads.findById(photoKey).orElseThrow(()->CourierException.invalid("Unknown photo. Upload the photo again."));
        if(!upload.courierId.equals(courierId)||!upload.parcelId.equals(parcelId))
            throw CourierException.forbidden("This photo was uploaded for another delivery.");
        if(upload.usedAt!=null)throw CourierException.conflict("This photo is already attached to another update.");
        byte[] bytes=storage.read(photoKey,MAX_BYTES);
        if(bytes==null)throw CourierException.invalid("The photo upload did not finish. Upload the photo again.");
        try {
            checkImage(bytes,EXTENSIONS.get(upload.contentType));
        } catch(CourierException e) {
            discard(upload);
            throw e;
        }
    }

    /** Call inside the transaction that saves the update, after {@link #verify}. */
    public void markUsed(String photoKey) {
        var upload=uploads.lockByKey(photoKey).orElseThrow(()->CourierException.invalid("Unknown photo. Upload the photo again."));
        if(upload.usedAt!=null)throw CourierException.conflict("This photo is already attached to another update.");
        upload.usedAt=Instant.now();
        uploads.save(upload);
    }

    public String viewUrl(String photoKey) {
        return photoKey==null?null:storage.viewUrl(photoKey);
    }

    /** Removes the stored file of an update that was rejected; the key can never be reused. */
    public void deleteFile(String photoKey) {
        if(photoKey==null)return;
        try{storage.delete(photoKey);}catch(RuntimeException e){log.warn("Could not delete rejected delivery photo {}",photoKey,e);}
    }

    /** Photos that were uploaded but never attached to an update are deleted after a day. */
    @Scheduled(fixedDelayString="${swiftdrop.photos.cleanup-interval:PT1H}",initialDelayString="PT5M")
    public void removeUnused() {
        for(var upload:uploads.findTop100ByUsedAtIsNullAndCreatedAtBefore(Instant.now().minus(UNUSED_LIFETIME))) {
            try{discard(upload);}catch(RuntimeException e){log.warn("Could not remove unused delivery photo {}",upload.photoKey,e);return;}
        }
    }

    private void discard(DeliveryPhotoUpload upload) {
        storage.delete(upload.photoKey);
        uploads.delete(upload);
    }

    static void checkImage(byte[] bytes,String extension) {
        if(bytes.length==0)throw CourierException.invalid("The photo is empty.");
        if(bytes.length>MAX_BYTES)throw new CourierException(HttpStatus.PAYLOAD_TOO_LARGE,"Photos must be at most 5 MB.");
        try(var image=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(image);
            if(!readers.hasNext())throw CourierException.invalid("The file is not a supported image.");
            var reader=readers.next();
            try {
                reader.setInput(image);
                String format=reader.getFormatName();
                boolean matches=extension.equals("png")?format.equalsIgnoreCase("png"):format.equalsIgnoreCase("jpeg")||format.equalsIgnoreCase("jpg");
                if(!matches)throw CourierException.invalid("Photo content does not match its type.");
                if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000L)throw CourierException.invalid("Photo dimensions are too large.");
                if(reader.read(0)==null)throw CourierException.invalid("The image cannot be decoded.");
            } finally {reader.dispose();}
        } catch(IOException e) {
            throw CourierException.invalid("The image cannot be decoded.");
        }
    }
}
