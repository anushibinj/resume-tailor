package com.resumetailor.jd;

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
 * Cached result of the JD-extraction LLM call. Keyed one-to-one to a job description,
 * so re-running against the same JD costs nothing.
 */
@Entity
@Table(name = "jd_analyses")
@Getter
@Setter
@NoArgsConstructor
public class JdAnalysis extends BaseEntity {

    @Column(name = "job_description_id", nullable = false)
    private UUID jobDescriptionId;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "company")
    private String company;

    @Column(name = "role")
    private String role;

    /** JSON array of {keyword, importance}. Read whole, never queried by field. */
    @Column(name = "keywords", nullable = false, columnDefinition = "text")
    private String keywords;

    @Column(name = "responsibilities", nullable = false, columnDefinition = "text")
    private String responsibilities;

    @Column(name = "must_haves", nullable = false, columnDefinition = "text")
    private String mustHaves;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
