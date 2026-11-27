package com.swiftdrop.courier.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;

@Configuration
public class PhotoStorageConfig {
    @Bean
    PhotoStorage photoStorage(@Value("${swiftdrop.photos.bucket:}") String bucket,
                              @Value("${swiftdrop.photos.region:ap-south-1}") String region,
                              @Value("${swiftdrop.photos.upload-ttl:5m}") Duration uploadTtl,
                              @Value("${swiftdrop.photos.view-ttl:5m}") Duration viewTtl) {
        if(bucket==null||bucket.isBlank())return new UnconfiguredPhotoStorage();
        var awsRegion=Region.of(region);
        return new S3PhotoStorage(S3Client.builder().region(awsRegion).build(),
            S3Presigner.builder().region(awsRegion).build(),bucket,uploadTtl,viewTtl);
    }
}
