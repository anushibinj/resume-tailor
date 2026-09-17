package com.resumetailor.keyword;

/**
 * Whether a JD keyword appears in the original resume and in the tailored one.
 *
 * <p>Comparing both is what makes the panel useful: it distinguishes "tailoring
 * surfaced something you already had" from "you never had this".
 */
public record KeywordMatch(
        String keyword,
        KeywordImportance importance,
        boolean presentInOriginal,
        boolean presentInTailored) {

    /** True when tailoring newly surfaced this keyword. */
    public boolean gained() {
        return presentInTailored && !presentInOriginal;
    }
}
