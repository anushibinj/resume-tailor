package com.resumetailor.keyword;

import java.util.Collection;

/**
 * Turns per-requirement coverage into a single percentage.
 *
 * <p>Coverage itself is judged by the user's model (see {@code GapAnalyzer}); this only
 * does the arithmetic, weighting a stated requirement above a nice-to-have so that
 * missing something the posting insists on costs more than missing a bonus.
 *
 * <p>An accepted addition counts as covered. That is applied here rather than stored, so
 * removing an addition takes the score back down without another model call.
 */
public final class CoverageScorer {

    private CoverageScorer() {
    }

    public record Requirement(KeywordImportance importance, boolean covered) {
    }

    /** Weighted percentage of requirements covered; 0 when nothing was extracted. */
    public static int score(Collection<Requirement> requirements) {
        int total = requirements.stream().mapToInt(r -> r.importance().weight()).sum();
        if (total == 0) {
            return 0;
        }
        int earned = requirements.stream()
                .filter(Requirement::covered)
                .mapToInt(r -> r.importance().weight())
                .sum();
        return Math.round((earned * 100f) / total);
    }
}
