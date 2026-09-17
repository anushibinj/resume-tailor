package com.resumetailor.export;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Database writes for compiled artifacts, kept separate from {@link ExportService} so a
 * PDF compile -- which can legitimately take a minute or two -- never runs inside an open
 * transaction holding a pooled connection.
 */
@Component
@RequiredArgsConstructor
public class ArtifactStore {

    static final String KIND_PDF = "PDF";

    private final ArtifactRepository artifactRepository;

    /** One stored PDF per run: recompiling after accepting a suggestion replaces it. */
    @Transactional
    public void replacePdf(UUID runId, String filePath, long sizeBytes, String compileLog) {
        artifactRepository.deleteAllByRunId(runId);

        Artifact artifact = new Artifact();
        artifact.setRunId(runId);
        artifact.setKind(KIND_PDF);
        artifact.setFilePath(filePath);
        artifact.setContentType(MediaType.APPLICATION_PDF_VALUE);
        artifact.setSizeBytes(sizeBytes);
        artifact.setCompileLog(compileLog);
        artifactRepository.save(artifact);
    }
}
