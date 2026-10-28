package com.resumetailor.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    @Mock
    private S3Client client;

    @Test
    void putsUnderTheBucketWithThePrefixAndContentType() {
        S3FileStorage storage = new S3FileStorage(client, "resumes", "/prod/");

        storage.put("users/u/runs/r/uploaded.pdf", new byte[] {1, 2}, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("resumes");
        assertThat(request.getValue().key()).isEqualTo("prod/users/u/runs/r/uploaded.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void blankPrefixLeavesTheKeyAsIs() {
        assertThat(new S3FileStorage(client, "b", "  ").objectKey("k.pdf")).isEqualTo("k.pdf");
        assertThat(new S3FileStorage(client, "b", null).objectKey("k.pdf")).isEqualTo("k.pdf");
    }

    @Test
    void readsTheObjectBytes() {
        given(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {7}));

        assertThat(new S3FileStorage(client, "b", "").get("k.pdf")).contains(new byte[] {7});
    }

    @Test
    void aMissingKeyReadsAsEmpty() {
        given(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().message("gone").build());

        assertThat(new S3FileStorage(client, "b", "").get("k.pdf")).isEmpty();
    }

    @Test
    void anUnreachableBucketIsAStorageError() {
        given(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> new S3FileStorage(client, "b", "").get("k.pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void deletesThePrefixedKey() {
        new S3FileStorage(client, "b", "p").delete("k.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("p/k.pdf");
    }
}
