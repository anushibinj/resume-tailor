package com.resumetailor.resume;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SectionSegmenterTest {

    @Test
    void splitsLatexSectionsAndKeepsTheHeaderBlock() {
        String body = """
                \\textbf{Jane Doe} \\\\ jane@example.com

                \\section{Experience}
                \\item Engineer at Acme

                \\section{Education}
                \\item BSc
                """;

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.LATEX);

        assertThat(sections).hasSize(3);
        assertThat(sections.get(0).title()).isNull();
        assertThat(sections.get(0).displayTitle()).isEqualTo("Header");
        assertThat(sections.get(1).title()).isEqualTo("Experience");
        assertThat(sections.get(1).content()).contains("Engineer at Acme");
        assertThat(sections.get(2).title()).isEqualTo("Education");
    }

    @Test
    void stripsMarkupFromSectionTitles() {
        String body = "\\section{\\textbf{Technical Skills}}\nJava";

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.LATEX);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).title()).isEqualTo("Technical Skills");
    }

    @Test
    void recognisesEnvironmentStyleSections() {
        String body = """
                \\begin{rSection}{Experience}
                Engineer
                \\end{rSection}
                """;

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.LATEX);

        assertThat(sections).extracting(ResumeSection::title).contains("Experience");
    }

    @Test
    void treatsSubsectionsAsDeeperLevels() {
        String body = "\\section{Experience}\nx\n\\subsection{Acme}\ny";

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.LATEX);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).level()).isEqualTo(1);
        assertThat(sections.get(1).level()).isEqualTo(2);
    }

    @Test
    void splitsMarkdownByHeadingLevel() {
        String body = """
                # Jane Doe
                jane@example.com

                ## Experience
                - Engineer at Acme

                ## Skills
                - Java
                """;

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.MARKDOWN);

        assertThat(sections).extracting(ResumeSection::title)
                .containsExactly("Jane Doe", "Experience", "Skills");
        assertThat(sections.get(0).level()).isEqualTo(1);
        assertThat(sections.get(1).level()).isEqualTo(2);
    }

    @Test
    void anUnrecognisedDocumentBecomesOneSectionRatherThanFailing() {
        String body = "just some free text with no headings at all";

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.MARKDOWN);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).content()).isEqualTo(body);
    }

    @Test
    void offsetsPointBackIntoTheOriginalBody() {
        String body = "# Jane\n\n## Skills\n- Java";

        List<ResumeSection> sections = SectionSegmenter.segment(body, ResumeFormat.MARKDOWN);

        for (ResumeSection section : sections) {
            assertThat(body.substring(section.startOffset(), section.endOffset()))
                    .as("offsets must slice back to exactly the section content")
                    .isEqualTo(section.content());
        }
    }
}
