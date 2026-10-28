package com.resumetailor.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    private Path root;

    @Test
    void writesReadsAndDeletesUnderTheRoot() {
        LocalFileStorage storage = new LocalFileStorage(root);
        byte[] pdf = "%PDF-1.4 body".getBytes(StandardCharsets.UTF_8);

        storage.put("users/u/runs/r/uploaded.pdf", pdf, "application/pdf");

        assertThat(root.resolve("users/u/runs/r/uploaded.pdf")).exists();
        assertThat(storage.get("users/u/runs/r/uploaded.pdf")).contains(pdf);

        storage.delete("users/u/runs/r/uploaded.pdf");

        assertThat(storage.get("users/u/runs/r/uploaded.pdf")).isEmpty();
    }

    @Test
    void replacesExistingContent() {
        LocalFileStorage storage = new LocalFileStorage(root);
        storage.put("a.pdf", new byte[] {1}, "application/pdf");

        storage.put("a.pdf", new byte[] {2, 3}, "application/pdf");

        assertThat(storage.get("a.pdf")).contains(new byte[] {2, 3});
    }

    @Test
    void deletingAMissingKeyIsNotAnError() {
        new LocalFileStorage(root).delete("users/nobody/runs/none/compiled.pdf");
    }

    @Test
    void refusesAKeyThatEscapesTheRoot() {
        LocalFileStorage storage = new LocalFileStorage(root);

        assertThatThrownBy(() -> storage.get("../outside.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsAndDeletesAnAbsolutePathWrittenBeforeStorageWasConfigurable() throws IOException {
        Path legacy = Files.createTempFile("legacy-artifact", ".pdf");
        Files.write(legacy, new byte[] {9});
        LocalFileStorage storage = new LocalFileStorage(root);

        assertThat(storage.get(legacy.toString())).contains(new byte[] {9});

        storage.delete(legacy.toString());

        assertThat(legacy).doesNotExist();
    }
}
