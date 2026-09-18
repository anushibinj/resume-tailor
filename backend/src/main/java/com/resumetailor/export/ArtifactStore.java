package com.resumetailor.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Database writes for compiled artifacts, kept separate from {@link ExportService} so a
 * PDF compile -- which can legitimately take a minute or two -- never runs inside an open
 * transaction holding a pooled connection.
 */
@Slf4j
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

    /**
     * Deletes this run's compiled file(s) from disk. Called before the run is deleted: the
     * {@code artifacts} row is cascade-deleted with it, but nothing else would clean up the
     * file it points to, leaving it orphaned on disk forever.
     */
    public void deleteFilesForRun(UUID runId) {
        for (Artifact artifact : artifactRepository.findAllByRunId(runId)) {
            try {
                Files.deleteIfExists(Path.of(artifact.getFilePath()));
            } catch (IOException ex) {
                log.warn("Could not delete artifact file {} for run {}: {}",
                        artifact.getFilePath(), runId, ex.getMessage());
            }
        }
    }
}
