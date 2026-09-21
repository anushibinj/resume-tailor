package com.resumetailor.tailoring;

/**
 * Lifecycle of the summary length options, tracked separately from the run because they are
 * written after the rewrite is already saved and can be regenerated on their own.
 */
public enum SummaryStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    /** The run predates summary options; the UI offers to generate them. */
    NONE
}
