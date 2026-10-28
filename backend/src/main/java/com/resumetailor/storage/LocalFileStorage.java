package com.resumetailor.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Keeps files in a directory on this machine ({@code STORAGE_LOCAL_PATH}). */
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Written beside the target and moved into place, so a reader never sees half a PDF.
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, content);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new StorageException("Could not save the file to " + target + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException ex) {
            return Optional.empty();
        } catch (IOException ex) {
            throw new StorageException("Could not read the stored file: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new StorageException("Could not delete the stored file: " + ex.getMessage(), ex);
        }
    }

    /**
     * Rows written before storage was configurable hold the absolute path the PDF was
     * written to, and are read from there. Every other key is relative to the root and
     * must stay inside it.
     */
    Path resolve(String key) {
        Path path = Path.of(key);
        if (path.isAbsolute()) {
            return path;
        }
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage directory: " + key);
        }
        return resolved;
    }
}
