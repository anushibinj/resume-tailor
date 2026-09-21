package com.resumetailor.tailoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Stores the summary length options on a run and swaps the chosen one into the body.
 *
 * <p>The model's rewrite is never edited: {@code tailored_body} keeps the summary the model
 * wrote, and the chosen variant replaces it only when the document is composed for display
 * and export (see {@link EffectiveBody}). That keeps the choice reversible and means a
 * regenerated set of options always starts from the same text.
 */
public final class SummaryVariants {

    public static final int MIN_LINES = 4;
    public static final int MAX_LINES = 7;
    /** Applied when options are first written; the slider moves it either way. */
    public static final int DEFAULT_LINES = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SummaryVariants() {
    }

    public static String toJson(List<SummaryOptions.Variant> variants) {
        try {
            return MAPPER.writeValueAsString(variants);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise summary options", ex);
        }
    }

    /** Tolerant on purpose: an unreadable column means "no options", not a broken run page. */
    public static List<SummaryOptions.Variant> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<SummaryOptions.Variant>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    /** The default length when the model wrote it, otherwise whichever length is closest. */
    public static Integer defaultSelection(List<SummaryOptions.Variant> variants) {
        return variants.stream()
                .map(SummaryOptions.Variant::lines)
                .min((a, b) -> {
                    int byDistance = Integer.compare(Math.abs(a - DEFAULT_LINES), Math.abs(b - DEFAULT_LINES));
                    return byDistance != 0 ? byDistance : Integer.compare(a, b);
                })
                .orElse(null);
    }

    /** The body with the run's chosen summary swapped in; unchanged when none is chosen. */
    public static String apply(String body, TailoringRun run) {
        Integer lines = run.getSummaryLines();
        String original = run.getSummaryOriginal();
        if (body == null || lines == null || original == null || original.isBlank()) {
            return body;
        }
        String replacement = fromJson(run.getSummaryVariants()).stream()
                .filter(variant -> variant.lines() == lines)
                .map(SummaryOptions.Variant::text)
                .findFirst()
                .orElse(null);
        if (replacement == null) {
            return body;
        }
        // Located the same way it was when the options were written, so a commented-out copy
        // of the same text earlier in a LaTeX body is never the one that gets replaced.
        int[] span = AdditionApplier.findAnchor(body, run.getFormat(), original);
        if (span == null) {
            return body;
        }
        return body.substring(0, span[0]) + replacement + body.substring(span[1]);
    }
}
