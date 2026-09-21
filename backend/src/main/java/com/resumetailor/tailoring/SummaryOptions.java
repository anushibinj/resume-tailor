package com.resumetailor.tailoring;

import java.util.List;

/**
 * The summary written at several lengths (LLM call 4).
 *
 * @param original the exact text of the summary inside the tailored body, which a chosen
 *                 variant replaces; null when the resume has no summary to resize
 * @param variants one per length, shortest first; empty when there is no summary
 */
public record SummaryOptions(String original, List<Variant> variants, String modelUsed) {

    /** @param lines the length asked for, in lines of about {@code charsPerLine} characters */
    public record Variant(int lines, String text) {
    }

    public static SummaryOptions none(String modelUsed) {
        return new SummaryOptions(null, List.of(), modelUsed);
    }
}
