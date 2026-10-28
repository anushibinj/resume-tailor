package com.resumetailor.storage;

import com.resumetailor.config.StorageProperties;
import com.resumetailor.config.StorageProperties.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageConfigTest {

    @TempDir
    private Path root;

    @Test
    void defaultsToLocalStorage() {
        FileStorage storage = new StorageConfig().fileStorage(
                new StorageProperties(null, root.toString(), DataSize.ofMegabytes(10), null));

        assertThat(storage).isInstanceOf(LocalFileStorage.class);
    }

    @Test
    void s3WithoutABucketRefusesToStart() {
        StorageProperties properties = new StorageProperties(StorageType.S3, root.toString(),
                DataSize.ofMegabytes(10), new StorageProperties.S3("", "us-east-1", "", false, "", "", ""));

        assertThatThrownBy(() -> new StorageConfig().fileStorage(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_BUCKET");
    }

    @Test
    void s3WithHalfACredentialRefusesToStart() {
        StorageProperties properties = new StorageProperties(StorageType.S3, root.toString(),
                DataSize.ofMegabytes(10), new StorageProperties.S3("b", "us-east-1", "", false, "key-id", "", ""));

        assertThatThrownBy(() -> new StorageConfig().fileStorage(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_SECRET_ACCESS_KEY");
    }

    @Test
    void buildsAnS3StorageWhenConfigured() {
        StorageProperties properties = new StorageProperties(StorageType.S3, root.toString(),
                DataSize.ofMegabytes(10),
                new StorageProperties.S3("b", "us-east-1", "http://localhost:9000", true, "id", "secret", "p"));

        assertThat(new StorageConfig().fileStorage(properties)).isInstanceOf(S3FileStorage.class);
    }
}
