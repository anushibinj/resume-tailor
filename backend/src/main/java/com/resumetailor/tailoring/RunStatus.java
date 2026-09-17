package com.resumetailor.tailoring;

public enum RunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
