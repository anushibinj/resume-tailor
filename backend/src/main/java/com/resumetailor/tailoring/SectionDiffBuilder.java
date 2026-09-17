package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.resume.ResumeSection;
import com.resumetailor.resume.SectionSegmenter;
import com.resumetailor.tailoring.TailoringDtos.SectionDiff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pairs original and tailored sections so the UI can show them side by side.
 *
 * <p>Pairing is by title, and the tailored document drives the order -- that is the
 * document the user is about to send, so reordering shows up as the reading order
 * rather than as a pile of moves. Sections dropped entirely are appended at the end so
 * removals are never invisible.
 */
public final class SectionDiffBuilder {

    private SectionDiffBuilder() {
    }

    public static List<SectionDiff> build(String originalBody, String tailoredBody, ResumeFormat format,
                                          List<RunChange> changes) {
        List<ResumeSection> originals = SectionSegmenter.segment(originalBody, format);
        Map<String, RunChange> changeByTitle = indexChanges(changes);

        if (tailoredBody == null || tailoredBody.isBlank()) {
            // Run still in flight or failed: show the original alone.
            return originals.stream()
                    .map(section -> new SectionDiff(
                            section.displayTitle(), section.level(), section.content(), "", null, null))
                    .toList();
        }

        List<ResumeSection> tailored = SectionSegmenter.segment(tailoredBody, format);
        Map<String, ResumeSection> originalByKey = new HashMap<>();
        for (ResumeSection section : originals) {
            originalByKey.putIfAbsent(key(section), section);
        }

        List<SectionDiff> diffs = new ArrayList<>();
        Set<String> consumed = new LinkedHashSet<>();

        for (ResumeSection section : tailored) {
            String key = key(section);
            ResumeSection counterpart = originalByKey.get(key);
            if (counterpart != null) {
                consumed.add(key);
            }
            RunChange change = changeByTitle.get(key);
            diffs.add(new SectionDiff(
                    section.displayTitle(),
                    section.level(),
                    counterpart != null ? counterpart.content() : "",
                    section.content(),
                    change != null ? change.getChangeType() : null,
                    change != null ? change.getRationale() : null));
        }

        for (ResumeSection section : originals) {
            if (!consumed.contains(key(section))) {
                diffs.add(new SectionDiff(
                        section.displayTitle(), section.level(), section.content(), "", "TRIMMED", null));
            }
        }
        return diffs;
    }

    private static Map<String, RunChange> indexChanges(List<RunChange> changes) {
        Map<String, RunChange> index = new HashMap<>();
        for (RunChange change : changes) {
            if (change.getSectionTitle() != null) {
                index.putIfAbsent(normalize(change.getSectionTitle()), change);
            }
        }
        return index;
    }

    private static String key(ResumeSection section) {
        return normalize(section.displayTitle());
    }

    private static String normalize(String title) {
        return title == null ? "" : title.strip().toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ");
    }
}
