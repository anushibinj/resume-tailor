package com.resumetailor.storage;

import com.resumetailor.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.nio.file.Path;

/**
 * Picks the one {@link FileStorage} this instance uses. A misconfigured S3 setup stops the
 * application at startup -- the same fail-fast rule as {@code ENCRYPTION_KEY} -- rather
 * than at the first PDF someone tries to keep.
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public FileStorage fileStorage(StorageProperties properties) {
        StorageProperties.StorageType type = properties.type() == null
                ? StorageProperties.StorageType.LOCAL
                : properties.type();
        if (type == StorageProperties.StorageType.S3) {
            StorageProperties.S3 s3 = properties.s3();
            if (s3 == null || s3.bucket() == null || s3.bucket().isBlank()) {
                throw new IllegalStateException(
                        "STORAGE_TYPE is s3 but S3_BUCKET is not set. Name the bucket PDFs are kept in,"
                                + " or set STORAGE_TYPE=local to keep them on disk.");
            }
            log.info("Storing PDFs in S3 bucket {}", s3.bucket());
            return new S3FileStorage(s3Client(s3), s3.bucket(), s3.keyPrefix());
        }
        Path root = Path.of(properties.localPath() == null || properties.localPath().isBlank()
                ? "./artifacts"
                : properties.localPath());
        log.info("Storing PDFs on disk in {}", root.toAbsolutePath().normalize());
        return new LocalFileStorage(root);
    }

    private static S3Client s3Client(StorageProperties.S3 s3) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3.region() == null || s3.region().isBlank() ? "us-east-1" : s3.region()))
                .forcePathStyle(s3.pathStyleAccess());
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint().trim()));
        }
        boolean hasKeyId = s3.accessKeyId() != null && !s3.accessKeyId().isBlank();
        boolean hasSecret = s3.secretAccessKey() != null && !s3.secretAccessKey().isBlank();
        if (hasKeyId != hasSecret) {
            throw new IllegalStateException(
                    "Set both S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY, or neither to use the AWS"
                            + " default credential chain.");
        }
        builder.credentialsProvider(hasKeyId
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKeyId(), s3.secretAccessKey()))
                : DefaultCredentialsProvider.builder().build());
        return builder.build();
    }
}
