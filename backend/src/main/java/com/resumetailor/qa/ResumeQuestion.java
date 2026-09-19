package com.resumetailor.qa;

import com.resumetailor.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One single-shot question asked about a resume, and its answer.
 *
 * <p>There is deliberately no thread or parent-question column: every row is answered from
 * the resume alone, never from earlier questions in this table, so this is a flat history
 * log, not a conversation. See {@link ResumeQaService}.
 */
@Entity
@Table(name = "resume_questions")
@Getter
@Setter
@NoArgsConstructor
public class ResumeQuestion extends AuditedEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "llm_profile_id")
    private UUID llmProfileId;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private QuestionStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;
}
