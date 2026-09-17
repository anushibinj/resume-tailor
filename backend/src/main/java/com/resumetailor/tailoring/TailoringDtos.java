package com.resumetailor.tailoring;

import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.resume.ResumeFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TailoringDtos {

    private TailoringDtos() {
    }

    /**
     * @param resumeId         optional; falls back to the user's default resume
     * @param jobDescriptionId reuse a stored JD, or supply {@code jdText} for a new one
     * @param llmProfileId     optional; falls back to the user's default LLM profile
     */
    public record CreateRunRequest(
            UUID resumeId,
            UUID jobDescriptionId,
            @Size(max = 200_000) String jdText,
            @Size(max = 255) String company,
            @Size(max = 255) String role,
            @Size(max = 1024) String sourceUrl,
            UUID llmProfileId) {
    }

    public record RunSummary(
            UUID id,
            RunStatus status,
            String company,
            String role,
            String resumeName,
            ResumeFormat format,
            Integer matchScore,
            Instant createdAt,
            Instant finishedAt) {
    }

    public record RunDetail(
            UUID id,
            RunStatus status,
            ResumeFormat format,
            String company,
            String role,
            UUID resumeId,
            String resumeName,
            UUID jobDescriptionId,
            String jobDescriptionText,
            String originalSource,
            String tailoredSource,
            String modelUsed,
            Integer promptTokens,
            Integer completionTokens,
            Integer matchScore,
            String errorMessage,
            List<SectionDiff> sections,
            List<SuggestionResponse> suggestions,
            List<KeywordResponse> keywords,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
    }

    /**
     * One section shown side by side. Either side may be blank: a section only the
     * tailored version has was added, one only the original has was dropped.
     */
    public record SectionDiff(
            String title,
            int level,
            String originalContent,
            String tailoredContent,
            String changeType,
            String rationale) {
    }

    public record SuggestionResponse(
            UUID id,
            SuggestionKind kind,
            String targetSection,
            String content,
            String rationale,
            SuggestionStatus status) {
    }

    public record KeywordResponse(
            String keyword,
            KeywordImportance importance,
            boolean presentInOriginal,
            boolean presentInTailored) {
    }

    public record UpdateSuggestionRequest(@NotNull SuggestionStatus status) {
    }
}
