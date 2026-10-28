package com.resumetailor.storage;

import java.util.Optional;

/**
 * Where stored files -- compiled and uploaded PDFs -- live. One implementation is active,
 * chosen by {@code STORAGE_TYPE} ({@link StorageConfig}).
 *
 * <p>Keys are always built by the application ({@link StorageKeys}), never taken from a
 * request, so an implementation never has to defend against a user-chosen path.
 */
public interface FileStorage {

    /** Writes the content under {@code key}, replacing whatever was there. */
    void put(String key, byte[] content, String contentType);

    /** The content under {@code key}, or empty when nothing is stored there. */
    Optional<byte[]> get(String key);

    /** Removes the content under {@code key}. A key that is already gone is not an error. */
    void delete(String key);
}
