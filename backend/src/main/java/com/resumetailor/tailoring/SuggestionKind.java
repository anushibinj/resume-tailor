package com.resumetailor.tailoring;

public enum SuggestionKind {
    BULLET,
    SKILL,
    SUMMARY;

    public static SuggestionKind parse(String raw) {
        if (raw == null) {
            return BULLET;
        }
        return switch (raw.trim().toUpperCase()) {
            case "SKILL", "SKILLS" -> SKILL;
            case "SUMMARY", "PROFILE", "OBJECTIVE" -> SUMMARY;
            default -> BULLET;
        };
    }
}
