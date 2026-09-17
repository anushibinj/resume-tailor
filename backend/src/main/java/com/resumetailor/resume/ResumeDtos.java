package com.resumetailor.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for /api/resumes. */
public final class ResumeDtos {

    private ResumeDtos() {
    }

    /**
     * @param format optional; when null the format is auto-detected from {@code sourceText}
     */
    public record SaveResumeRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank String sourceText,
            ResumeFormat format,
            Boolean makeDefault) {
    }

    public record ResumeSummary(
            UUID id,
            String name,
            ResumeFormat format,
            boolean isDefault,
            int sectionCount,
            int characterCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ResumeDetail(
            UUID id,
            String name,
            ResumeFormat format,
            boolean isDefault,
            String sourceText,
            String preamble,
            String bodyText,
            String documentTail,
            List<SectionSummary> sections,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SectionSummary(String title, int level, int characterCount) {
    }
}
