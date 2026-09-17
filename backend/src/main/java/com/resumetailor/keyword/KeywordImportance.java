package com.resumetailor.keyword;

/**
 * How much a JD keyword counts toward the match score.
 *
 * <p>The weights are what make the score meaningful: missing a stated requirement
 * should hurt more than missing a nice-to-have.
 */
public enum KeywordImportance {
    REQUIRED(3),
    PREFERRED(2),
    NICE(1);

    private final int weight;

    KeywordImportance(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /** Lenient parsing: models occasionally return lowercase or unexpected labels. */
    public static KeywordImportance parse(String raw) {
        if (raw == null) {
            return NICE;
        }
        return switch (raw.trim().toUpperCase()) {
            case "REQUIRED", "MUST", "MUST_HAVE", "MUST-HAVE" -> REQUIRED;
            case "PREFERRED", "SHOULD", "DESIRED" -> PREFERRED;
            default -> NICE;
        };
    }
}
