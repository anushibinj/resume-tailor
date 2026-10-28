package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where PDFs -- compiled in the app or uploaded by the user -- are kept.
 *
 * @param type          {@code local} (a directory on this machine) or {@code s3}
 * @param localPath     directory used when {@code type} is {@code local}
 * @param maxUploadSize largest PDF a user may upload; the body is read no further than this
 * @param s3            bucket settings used when {@code type} is {@code s3}
 */
@ConfigurationProperties(prefix = "resume-tailor.storage")
public record StorageProperties(
        StorageType type,
        String localPath,
        DataSize maxUploadSize,
        S3 s3) {

    public enum StorageType { LOCAL, S3 }

    /**
     * @param bucket          REQUIRED when {@code type} is {@code s3}
     * @param region          e.g. {@code us-east-1}; any value works for most S3-compatible servers
     * @param endpoint        blank for AWS; set for MinIO, Cloudflare R2, Backblaze B2 and the like
     * @param pathStyleAccess true for servers that do not support virtual-hosted buckets (MinIO)
     * @param accessKeyId     blank uses the AWS default credential chain (env, profile, IAM role)
     * @param secretAccessKey paired with {@code accessKeyId}
     * @param keyPrefix       prepended to every object key, so the bucket can be shared
     */
    public record S3(
            String bucket,
            String region,
            String endpoint,
            boolean pathStyleAccess,
            String accessKeyId,
            String secretAccessKey,
            String keyPrefix) {
    }
}
