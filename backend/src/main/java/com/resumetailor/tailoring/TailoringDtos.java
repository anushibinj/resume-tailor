package com.resumetailor.tailoring;

import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.resume.ResumeFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            boolean applied,
            Instant appliedAt,
            String applicationLink,
            Instant createdAt,
            Instant finishedAt) {
    }

    public record RunDetail(
            UUID id,
            RunStatus status,
            GapsStatus gapsStatus,
            String gapsError,
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
            List<KeywordResponse> keywords,
            /** Additions not tied to a current requirement, e.g. ones kept from an earlier check. */
            List<SuggestionResponse> otherSuggestions,
            SummaryResponse summary,
            boolean applied,
            Instant appliedAt,
            String applicationLink,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
    }

    /** The applied flag and link alone, returned by the two endpoints that only touch those. */
    public record RunApplicationResponse(
            UUID id,
            boolean applied,
            Instant appliedAt,
            String applicationLink) {
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

    /**
     * A requirement from the posting and whether the resume covers it.
     *
     * @param covered  the model's judgment; {@code evidence} quotes the resume text behind it
     * @param addition offered when it is not covered, so the gap can be closed in one click
     * @param description what the requirement is, from the glossary shared by all users;
     *                    null until some model has explained it
     */
    public record KeywordResponse(
            String keyword,
            KeywordImportance importance,
            boolean covered,
            String evidence,
            SuggestionResponse addition,
            String description) {
    }

    public record SuggestionResponse(
            UUID id,
            SuggestionKind kind,
            String keyword,
            String targetSection,
            String content,
            String rationale,
            SuggestionStatus status) {
    }

    /**
     * The summary at several lengths.
     *
     * @param selectedLines the length in the document now; null when the summary is exactly as
     *                      the model wrote it (no options yet, or none could be made)
     * @param variants      shortest first; empty until options exist, or when the resume has no
     *                      summary to resize
     */
    public record SummaryResponse(
            SummaryStatus status,
            String error,
            Integer selectedLines,
            List<SummaryVariantResponse> variants) {
    }

    /** @param lines the length asked for; an estimate, since only compiling shows real lines */
    public record SummaryVariantResponse(int lines, String text) {
    }

    public record SelectSummaryRequest(
            @NotNull @Min(SummaryVariants.MIN_LINES) @Max(SummaryVariants.MAX_LINES) Integer lines) {
    }

    public record UpdateSuggestionRequest(@NotNull SuggestionStatus status) {
    }

    public record UpdateAppliedRequest(@NotNull Boolean applied) {
    }

    /** A blank or missing link clears it; free text, not validated as a URL. */
    public record UpdateApplicationLinkRequest(@Size(max = 2048) String applicationLink) {
    }
}
