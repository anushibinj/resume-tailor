package com.resumetailor.tailoring;

import com.resumetailor.common.AuditedEntity;
import com.resumetailor.resume.ParsedResume;
import com.resumetailor.resume.ResumeFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One tailoring attempt: a resume, a job description, and what the model produced.
 *
 * <p>The {@code original*} columns snapshot the resume as it was when the run started.
 * Base resumes can be edited afterwards, and history has to stay faithful to what was
 * actually sent -- otherwise a diff viewed next month would compare against text that
 * never went to the model.
 */
@Entity
@Table(name = "tailoring_runs")
@Getter
@Setter
@NoArgsConstructor
public class TailoringRun extends AuditedEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "job_description_id", nullable = false)
    private UUID jobDescriptionId;

    @Column(name = "llm_profile_id")
    private UUID llmProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private ResumeFormat format;

    @Column(name = "original_source", nullable = false, columnDefinition = "text")
    private String originalSource;

    @Column(name = "original_body", nullable = false, columnDefinition = "text")
    private String originalBody;

    @Column(name = "preamble", nullable = false, columnDefinition = "text")
    private String preamble;

    @Column(name = "document_tail", nullable = false, columnDefinition = "text")
    private String documentTail;

    @Column(name = "tailored_body", columnDefinition = "text")
    private String tailoredBody;

    @Column(name = "tailored_source", columnDefinition = "text")
    private String tailoredSource;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** Gap analysis has its own lifecycle: it runs after the rewrite and can be re-run. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gaps_status", nullable = false, length = 16)
    private GapsStatus gapsStatus;

    @Column(name = "gaps_error", columnDefinition = "text")
    private String gapsError;

    /** Summary length options are written after the rewrite and can be regenerated alone. */
    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false, length = 16)
    private SummaryStatus summaryStatus;

    @Column(name = "summary_error", columnDefinition = "text")
    private String summaryError;

    /** Exact text of the summary inside {@link #tailoredBody}, which a chosen variant replaces. */
    @Column(name = "summary_original", columnDefinition = "text")
    private String summaryOriginal;

    /** JSON array of {@link SummaryOptions.Variant}; read through {@link SummaryVariants}. */
    @Column(name = "summary_variants", columnDefinition = "text")
    private String summaryVariants;

    /** The length currently chosen; null means the summary exactly as the model wrote it. */
    @Column(name = "summary_lines")
    private Integer summaryLines;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Recorded by the user, not inferred: several runs can be queued before any is applied to. */
    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "applied_at")
    private Instant appliedAt;

    /** The posting or application-tracker URL for this run; free text, not validated as a URL. */
    @Column(name = "application_link", columnDefinition = "text")
    private String applicationLink;

    /** The document skeleton this run must rebuild into; the preamble is never re-derived. */
    public ParsedResume skeleton() {
        return new ParsedResume(format, preamble, originalBody, documentTail);
    }
}
