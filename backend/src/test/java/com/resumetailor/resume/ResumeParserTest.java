package com.resumetailor.resume;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeParserTest {

    private static final String LATEX_RESUME = """
            \\documentclass[11pt]{article}
            \\usepackage{geometry}
            \\newcommand{\\resumeItem}[1]{\\item #1}

            \\begin{document}
            \\section{Experience}
            \\item Built things
            \\end{document}
            """;

    @Nested
    @DisplayName("format detection")
    class Detection {

        @Test
        void detectsLatexFromDocumentClass() {
            assertThat(ResumeParser.detectFormat(LATEX_RESUME)).isEqualTo(ResumeFormat.LATEX);
        }

        @Test
        void detectsMarkdownFromPlainHeadings() {
            String markdown = """
                    # Jane Doe
                    jane@example.com

                    ## Experience
                    - Built things
                    """;
            assertThat(ResumeParser.detectFormat(markdown)).isEqualTo(ResumeFormat.MARKDOWN);
        }

        @Test
        void detectsLatexFragmentWithoutPreamble() {
            String fragment = """
                    \\section{Experience}
                    \\begin{itemize}
                    \\item \\textbf{Engineer} at \\emph{Acme}
                    \\end{itemize}
                    """;
            assertThat(ResumeParser.detectFormat(fragment)).isEqualTo(ResumeFormat.LATEX);
        }

        @Test
        void treatsBlankInputAsMarkdown() {
            assertThat(ResumeParser.detectFormat("")).isEqualTo(ResumeFormat.MARKDOWN);
            assertThat(ResumeParser.detectFormat(null)).isEqualTo(ResumeFormat.MARKDOWN);
        }
    }

    @Nested
    @DisplayName("preamble protection")
    class PreambleSplit {

        @Test
        void keepsPreambleOutOfTheBody() {
            ParsedResume parsed = ResumeParser.parse(LATEX_RESUME);

            assertThat(parsed.preamble())
                    .contains("\\documentclass")
                    .contains("\\usepackage{geometry}")
                    .contains("\\newcommand{\\resumeItem}")
                    .endsWith("\\begin{document}");
            assertThat(parsed.body())
                    .contains("\\section{Experience}")
                    .doesNotContain("\\documentclass")
                    .doesNotContain("\\usepackage");
            assertThat(parsed.documentTail()).contains("\\end{document}");
        }

        @Test
        void markdownHasNoPreambleToProtect() {
            String markdown = "# Jane\n\n## Skills\n- Java";
            ParsedResume parsed = ResumeParser.parse(markdown);

            assertThat(parsed.preamble()).isEmpty();
            assertThat(parsed.documentTail()).isEmpty();
            assertThat(parsed.body()).isEqualTo(markdown);
        }

        @Test
        void latexFragmentIsAllBody() {
            String fragment = "\\section{Skills}\n\\item Java \\item Kotlin \\item Go \\item Rust";
            ParsedResume parsed = ResumeParser.parse(fragment);

            assertThat(parsed.preamble()).isEmpty();
            assertThat(parsed.body()).isEqualTo(fragment);
        }
    }

    @Nested
    @DisplayName("reassembly")
    class Reassembly {

        @Test
        void roundTripsAnUnchangedBodyBackIntoACompilableDocument() {
            ParsedResume parsed = ResumeParser.parse(LATEX_RESUME);
            String rebuilt = parsed.reassemble(parsed.body());

            assertThat(rebuilt)
                    .startsWith("\\documentclass[11pt]{article}")
                    .contains("\\begin{document}")
                    .contains("\\section{Experience}")
                    .endsWith("\\end{document}\n");
        }

        @Test
        void preambleSurvivesAModelRewritingTheBody() {
            ParsedResume parsed = ResumeParser.parse(LATEX_RESUME);
            String rebuilt = parsed.reassemble("\\section{Experience}\n\\item Shipped things");

            assertThat(rebuilt).contains("\\newcommand{\\resumeItem}[1]{\\item #1}");
            assertThat(rebuilt).contains("Shipped things");
            assertThat(rebuilt).doesNotContain("Built things");
        }

        @Test
        void normalisesBlankLinesTheModelMayHaveAdded() {
            ParsedResume parsed = ResumeParser.parse(LATEX_RESUME);
            String rebuilt = parsed.reassemble("\n\n\\section{Skills}\n\n\n");

            assertThat(rebuilt).doesNotContain("\n\n\n");
            assertThat(rebuilt).endsWith("\\end{document}\n");
        }
    }
}
