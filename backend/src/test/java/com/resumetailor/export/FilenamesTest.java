package com.resumetailor.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenamesTest {

    @Test
    void buildsASlugFromCompanyAndRole() {
        assertThat(Filenames.forRun("Acme Corp", "Senior Backend Engineer", "tex"))
                .isEqualTo("resume-acme-corp-senior-backend-engineer.tex");
    }

    @Test
    void dropsCharactersThatAreUnsafeInAHeader() {
        assertThat(Filenames.forRun("Acme, Inc. \"Ltd\"", "C++ / Go Engineer", "pdf"))
                .isEqualTo("resume-acme-inc-ltd-c-go-engineer.pdf");
    }

    @Test
    void handlesMissingLabels() {
        assertThat(Filenames.forRun(null, null, "md")).isEqualTo("resume.md");
        assertThat(Filenames.forRun("", "  ", "tex")).isEqualTo("resume.tex");
    }

    @Test
    void truncatesOverlongLabels() {
        String filename = Filenames.forRun("A".repeat(120), "B".repeat(120), "tex");

        assertThat(filename.length()).isLessThan(100);
    }
}
