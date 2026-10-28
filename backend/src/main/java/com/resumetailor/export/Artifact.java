package com.resumetailor.export;

import com.resumetailor.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A PDF kept in the configured storage, linked to its run: either compiled by the app
 * ({@code PDF}) or uploaded by the user ({@code UPLOADED_PDF}).
 */
@Entity
@Table(name = "artifacts")
@Getter
@Setter
@NoArgsConstructor
public class Artifact extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    /** Key in {@link com.resumetailor.storage.FileStorage}; see {@code V7__stored_pdfs.sql}. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "compile_log", columnDefinition = "text")
    private String compileLog;

    /** The uploaded file's own name, for display only. Null for compiled PDFs. */
    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
