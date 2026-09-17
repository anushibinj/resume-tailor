package com.resumetailor.tailoring;

/**
 * Lifecycle of the gap analysis, tracked separately from the run because it happens
 * after the rewrite is already saved and can be re-run on its own.
 */
public enum GapsStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    /** Coverage came from the retired keyword matcher; the UI offers a re-check instead. */
    OUTDATED;

    public boolean isUsable() {
        return this == COMPLETED;
    }
}
