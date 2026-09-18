package com.resumetailor.keyword;

/**
 * The matching key for a requirement's wording, so the same skill phrased slightly
 * differently is recognised as the same one.
 *
 * <p>Parentheticals are dropped because models decorate the keyword they echo back:
 * "Java (REQUIRED)" when the importance was shown beside it, "Java (Programming
 * Language)" when glossing. A mismatch while reading the gap analysis throws the whole
 * analysis away, so this errs toward matching.
 *
 * <p>A trailing {@code +} or {@code #} is kept: it is part of the skill's name, and the key
 * also indexes the shared skill glossary, where "C", "C++" and "C#" must stay three entries.
 */
public final class KeywordNormalizer {

    private KeywordNormalizer() {
    }

    public static String normalize(String keyword) {
        return keyword.replaceAll("\\([^)]*\\)", " ")
                .replaceAll("[\\s\\p{Punct}&&[^+#]]+$", "")
                .replaceAll("^[\\s\\p{Punct}&&[^+#]]+", "")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase();
    }
}
