package com.resumetailor.storage;

import java.util.UUID;

/**
 * Builds every storage key in one place. Keys are grouped by owner first, so one user's
 * files can be found -- or removed -- with a single prefix.
 */
public final class StorageKeys {

    private StorageKeys() {
    }

    /** The PDF the app compiled for a run. Recompiling overwrites it. */
    public static String compiledPdf(UUID ownerId, UUID runId) {
        return run(ownerId, runId) + "/compiled.pdf";
    }

    /** The PDF the user made elsewhere and uploaded for a run. Re-uploading overwrites it. */
    public static String uploadedPdf(UUID ownerId, UUID runId) {
        return run(ownerId, runId) + "/uploaded.pdf";
    }

    private static String run(UUID ownerId, UUID runId) {
        return "users/" + ownerId + "/runs/" + runId;
    }
}
