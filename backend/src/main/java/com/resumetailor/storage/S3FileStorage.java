package com.resumetailor.storage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

/** Keeps files in an S3 bucket, or any S3-compatible server (MinIO, R2, B2). */
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final String bucket;
    private final String keyPrefix;

    public S3FileStorage(S3Client client, String bucket, String keyPrefix) {
        this.client = client;
        this.bucket = bucket;
        this.keyPrefix = normalisePrefix(keyPrefix);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(objectKey(key)).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new StorageException("Could not save the file to bucket " + bucket + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey(key)).build()).asByteArray());
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (SdkException ex) {
            throw new StorageException("Could not read from bucket " + bucket + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(String key) {
        // S3 reports success for a key that does not exist, which is the contract here too.
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
        } catch (SdkException ex) {
            throw new StorageException("Could not delete from bucket " + bucket + ": " + ex.getMessage(), ex);
        }
    }

    String objectKey(String key) {
        return keyPrefix + key;
    }

    private static String normalisePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String trimmed = prefix.trim().replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? "" : trimmed + "/";
    }
}
