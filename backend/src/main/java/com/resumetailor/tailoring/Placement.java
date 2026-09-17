package com.resumetailor.tailoring;

/** Where an addition goes relative to its anchor text. */
public enum Placement {
    AFTER,
    BEFORE;

    public static Placement parse(String raw) {
        return raw != null && raw.trim().equalsIgnoreCase("BEFORE") ? BEFORE : AFTER;
    }
}
