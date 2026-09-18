package com.resumetailor.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArtifactStoreTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Test
    void deletesEveryFileOnDiskForTheRun() throws IOException {
        UUID runId = UUID.randomUUID();
        Path pdf = Files.createTempFile("artifact-store-test", ".pdf");
        Artifact artifact = new Artifact();
        artifact.setRunId(runId);
        artifact.setFilePath(pdf.toAbsolutePath().toString());
        given(artifactRepository.findAllByRunId(runId)).willReturn(List.of(artifact));

        new ArtifactStore(artifactRepository).deleteFilesForRun(runId);

        assertThat(Files.exists(pdf)).isFalse();
    }

    @Test
    void toleratesAFileThatIsAlreadyGone() {
        UUID runId = UUID.randomUUID();
        Artifact artifact = new Artifact();
        artifact.setRunId(runId);
        artifact.setFilePath("/no/such/path/" + UUID.randomUUID() + ".pdf");
        given(artifactRepository.findAllByRunId(runId)).willReturn(List.of(artifact));

        new ArtifactStore(artifactRepository).deleteFilesForRun(runId);
    }
}
