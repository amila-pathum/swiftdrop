package com.swiftdrop.courier.photo;

import com.swiftdrop.courier.exception.CourierException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** Photos in a private S3 bucket. Credentials come from the default AWS chain (AWS_ACCESS_KEY_ID etc.). */
public class S3PhotoStorage implements PhotoStorage {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration uploadTtl;
    private final Duration viewTtl;

    public S3PhotoStorage(S3Client s3,S3Presigner presigner,String bucket,Duration uploadTtl,Duration viewTtl) {
        this.s3=s3;this.presigner=presigner;this.bucket=bucket;this.uploadTtl=uploadTtl;this.viewTtl=viewTtl;
    }

    @Override public UploadTicket presignUpload(String key,String contentType,long sizeBytes) {
        // Content type and length are part of the signature, so S3 refuses any other file.
        var put=PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes).build();
        var signed=presigner.presignPutObject(r->r.signatureDuration(uploadTtl).putObjectRequest(put));
        return new UploadTicket(signed.url().toString(),Map.of("Content-Type",contentType),signed.expiration());
    }

    @Override public byte[] read(String key,int maxBytes) {
        try(ResponseInputStream<GetObjectResponse> in=s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return in.readNBytes(maxBytes+1);
        } catch(S3Exception e) {
            // Without s3:ListBucket (least privilege), S3 reports a missing key as 403, not 404.
            if(e.statusCode()==404||e.statusCode()==403)return null;
            throw unavailable(e);
        } catch(SdkException|IOException e) {
            throw unavailable(e);
        }
    }

    @Override public String viewUrl(String key) {
        var get=GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(r->r.signatureDuration(viewTtl).getObjectRequest(get)).url().toString();
    }

    @Override public void delete(String key) {
        try{s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());}
        catch(SdkException e){throw unavailable(e);}
    }

    private static CourierException unavailable(Exception cause) {
        var error=new CourierException(HttpStatus.SERVICE_UNAVAILABLE,"Photo storage is unavailable. Try again shortly.");
        error.initCause(cause);
        return error;
    }
}
