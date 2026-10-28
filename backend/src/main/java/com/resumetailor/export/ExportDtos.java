package com.resumetailor.export;

import java.time.Instant;

public final class ExportDtos {

    private ExportDtos() {
    }

    /** A PDF kept for a run. {@code originalFilename} is null for one the app compiled. */
    public record StoredPdf(long sizeBytes, String originalFilename, Instant storedAt) {

        static StoredPdf of(Artifact artifact) {
            return new StoredPdf(artifact.getSizeBytes(), artifact.getOriginalFilename(), artifact.getCreatedAt());
        }
    }

    /** The PDFs kept for a run; either is null when there is none. */
    public record StoredPdfs(StoredPdf compiled, StoredPdf uploaded) {
    }
}
