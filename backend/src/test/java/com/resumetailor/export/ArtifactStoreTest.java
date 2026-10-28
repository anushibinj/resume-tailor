package com.resumetailor.export;

import com.resumetailor.storage.FileStorage;
import com.resumetailor.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArtifactStoreTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private FileStorage fileStorage;

    @Test
    void deletesEveryStoredFileForTheRun() {
        UUID runId = UUID.randomUUID();
        given(artifactRepository.findAllByRunId(runId))
                .willReturn(List.of(artifact(runId, "compiled-key"), artifact(runId, "uploaded-key")));

        new ArtifactStore(artifactRepository, fileStorage).deleteFilesForRun(runId);

        verify(fileStorage).delete("compiled-key");
        verify(fileStorage).delete("uploaded-key");
    }

    @Test
    void aStorageFailureDoesNotStopTheRestBeingDeleted() {
        UUID runId = UUID.randomUUID();
        given(artifactRepository.findAllByRunId(runId))
                .willReturn(List.of(artifact(runId, "first"), artifact(runId, "second")));
        willThrow(new StorageException("bucket unreachable", null)).given(fileStorage).delete("first");

        new ArtifactStore(artifactRepository, fileStorage).deleteFilesForRun(runId);

        verify(fileStorage).delete("second");
    }

    @Test
    void replacingOneKindLeavesTheOtherAlone() {
        UUID runId = UUID.randomUUID();
        Artifact previous = artifact(runId, "old-key");
        given(artifactRepository.findAllByRunIdAndKind(runId, ArtifactStore.KIND_UPLOADED_PDF))
                .willReturn(List.of(previous));

        List<String> replaced = new ArtifactStore(artifactRepository, fileStorage)
                .replace(runId, ArtifactStore.KIND_UPLOADED_PDF, "new-key", 42, null, "mine.pdf");

        assertThat(replaced).containsExactly("old-key");
        verify(artifactRepository).deleteAll(List.of(previous));
        ArgumentCaptor<Artifact> saved = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(ArtifactStore.KIND_UPLOADED_PDF);
        assertThat(saved.getValue().getStorageKey()).isEqualTo("new-key");
        assertThat(saved.getValue().getOriginalFilename()).isEqualTo("mine.pdf");
    }

    private static Artifact artifact(UUID runId, String key) {
        Artifact artifact = new Artifact();
        artifact.setRunId(runId);
        artifact.setStorageKey(key);
        return artifact;
    }
}
