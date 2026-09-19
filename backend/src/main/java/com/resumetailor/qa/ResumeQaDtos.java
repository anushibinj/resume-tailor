package com.resumetailor.qa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request/response shapes for /api/resumes/{resumeId}/questions. */
public final class ResumeQaDtos {

    private ResumeQaDtos() {
    }

    /** @param llmProfileId optional; falls back to the user's default LLM profile */
    public record AskQuestionRequest(@NotBlank @Size(max = 4000) String question, UUID llmProfileId) {
    }

    public record ResumeQuestionResponse(
            UUID id,
            UUID resumeId,
            String question,
            String answer,
            QuestionStatus status,
            String errorMessage,
            String modelUsed,
            Instant createdAt) {
    }
}
