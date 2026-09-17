package com.resumetailor.jd;

import com.resumetailor.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "job_descriptions")
@Getter
@Setter
@NoArgsConstructor
public class JobDescription extends AuditedEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "company")
    private String company;

    @Column(name = "role")
    private String role;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    /** SHA-256 of {@link #rawText}; lets an identical JD reuse a cached analysis. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
}
