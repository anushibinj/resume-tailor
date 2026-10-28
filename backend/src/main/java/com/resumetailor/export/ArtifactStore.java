package com.resumetailor.export;

import com.resumetailor.storage.FileStorage;
import com.resumetailor.storage.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Database writes for stored artifacts, kept separate from {@link ExportService} so a
 * PDF compile -- which can legitimately take a minute or two -- never runs inside an open
 * transaction holding a pooled connection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtifactStore {

    /** A PDF the app compiled. */
    static final String KIND_PDF = "PDF";
    /** A PDF the user made elsewhere and uploaded. */
    static final String KIND_UPLOADED_PDF = "UPLOADED_PDF";

    private final ArtifactRepository artifactRepository;
    private final FileStorage fileStorage;

    /**
     * One stored PDF of each kind per run: recompiling after accepting a suggestion replaces
     * the compiled one, uploading again replaces the uploaded one, and neither touches the
     * other.
     *
     * @return the storage keys of the rows replaced, so the caller can remove any file that
     *         the new row no longer points at
     */
    @Transactional
    public List<String> replace(UUID runId, String kind, String storageKey, long sizeBytes,
                                String compileLog, String originalFilename) {
        List<String> replaced = removeRows(runId, kind);

        Artifact artifact = new Artifact();
        artifact.setRunId(runId);
        artifact.setKind(kind);
        artifact.setStorageKey(storageKey);
        artifact.setContentType(MediaType.APPLICATION_PDF_VALUE);
        artifact.setSizeBytes(sizeBytes);
        artifact.setCompileLog(compileLog);
        artifact.setOriginalFilename(originalFilename);
        artifactRepository.save(artifact);
        return replaced;
    }

    /** Deletes this run's rows of one kind and returns the storage keys they pointed at. */
    @Transactional
    public List<String> removeRows(UUID runId, String kind) {
        List<Artifact> existing = artifactRepository.findAllByRunIdAndKind(runId, kind);
        artifactRepository.deleteAll(existing);
        return existing.stream().map(Artifact::getStorageKey).toList();
    }

    /**
     * Deletes this run's stored file(s). Called before the run is deleted: the
     * {@code artifacts} row is cascade-deleted with it, but nothing else would clean up the
     * file it points to, leaving it orphaned in storage forever.
     */
    public void deleteFilesForRun(UUID runId) {
        deleteFiles(runId, artifactRepository.findAllByRunId(runId).stream()
                .map(Artifact::getStorageKey)
                .toList());
    }

    /**
     * Best effort: the row is already gone or going, and a file left behind costs only
     * space, so a storage failure is logged rather than failing what the user asked for.
     */
    void deleteFiles(UUID runId, List<String> storageKeys) {
        for (String key : storageKeys) {
            try {
                fileStorage.delete(key);
            } catch (StorageException | IllegalArgumentException ex) {
                log.warn("Could not delete stored file {} for run {}: {}", key, runId, ex.getMessage());
            }
        }
    }
}
