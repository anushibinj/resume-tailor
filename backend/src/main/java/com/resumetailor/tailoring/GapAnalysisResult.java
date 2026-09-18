package com.resumetailor.tailoring;

import com.resumetailor.keyword.JdKeyword;

import java.util.List;
import java.util.UUID;

/** What the model concluded about each requirement (LLM call 3). */
public record GapAnalysisResult(List<RequirementVerdict> verdicts, String modelUsed) {

    /**
     * @param covered     the model's judgment, by meaning rather than string matching
     * @param evidence    verbatim quote from the resume backing a covered verdict
     * @param addressedBy an addition the user already accepted that satisfies this
     * @param addition    how to add it, present whenever it is not covered
     */
    public record RequirementVerdict(
            JdKeyword requirement,
            boolean covered,
            String evidence,
            UUID addressedBy,
            ProposedAddition addition) {
    }

    /**
     * An insert-only edit: {@code insert} goes immediately after or before {@code anchor},
     * which must be text already in the resume. Without an anchor the app falls back to
     * placing it by section.
     */
    public record ProposedAddition(
            String label,
            SuggestionKind kind,
            String section,
            String anchor,
            Placement placement,
            String insert) {
    }
}
