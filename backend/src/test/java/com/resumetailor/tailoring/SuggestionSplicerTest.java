package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionSplicerTest {

    private static final String LATEX_BODY = """
            \\section{Experience}
            \\begin{itemize}
            \\item Built the billing service
            \\end{itemize}

            \\section{Skills}
            \\begin{itemize}
            \\item Java
            \\end{itemize}
            """;

    private static final String MARKDOWN_BODY = """
            ## Experience
            - Built the billing service

            ## Skills
            - Java
            """;

    @Test
    void addsALatexItemInsideTheTargetSectionList() {
        String result = SuggestionSplicer.splice(
                LATEX_BODY, ResumeFormat.LATEX, SuggestionKind.SKILL, "Skills", "Terraform");

        assertThat(result).contains("\\item Java");
        assertThat(result).contains("\\item Terraform");
        // Inserted into Skills, not Experience.
        assertThat(result.indexOf("Terraform")).isGreaterThan(result.indexOf("\\section{Skills}"));
        assertThat(result).contains("Built the billing service");
    }

    @Test
    void addsAMarkdownBulletMatchingTheSurroundingMarker() {
        String result = SuggestionSplicer.splice(
                MARKDOWN_BODY, ResumeFormat.MARKDOWN, SuggestionKind.SKILL, "Skills", "Terraform");

        assertThat(result).contains("- Java");
        assertThat(result).contains("- Terraform");
        assertThat(result.indexOf("Terraform")).isGreaterThan(result.indexOf("## Skills"));
    }

    @Test
    void findsASectionByPartialTitle() {
        String body = LATEX_BODY.replace("\\section{Skills}", "\\section{Technical Skills}");

        String result = SuggestionSplicer.splice(
                body, ResumeFormat.LATEX, SuggestionKind.SKILL, "Skills", "Terraform");

        assertThat(result.indexOf("Terraform")).isGreaterThan(result.indexOf("Technical Skills"));
    }

    @Test
    void appendsRatherThanDroppingWhenTheSectionDoesNotExist() {
        String result = SuggestionSplicer.splice(
                MARKDOWN_BODY, ResumeFormat.MARKDOWN, SuggestionKind.BULLET, "Publications", "Wrote a paper");

        assertThat(result).contains("Wrote a paper");
    }

    @Test
    void escapesCharactersThatWouldBreakTheLatexBuild() {
        String result = SuggestionSplicer.splice(
                LATEX_BODY, ResumeFormat.LATEX, SuggestionKind.BULLET, "Experience",
                "Improved throughput 30% for R&D workflows using snake_case configs");

        assertThat(result).contains("30\\%");
        assertThat(result).contains("R\\&D");
        assertThat(result).contains("snake\\_case");
    }

    @Test
    void leavesIntentionalLatexMarkupAlone() {
        String result = SuggestionSplicer.splice(
                LATEX_BODY, ResumeFormat.LATEX, SuggestionKind.BULLET, "Experience",
                "Led \\textbf{platform} migration");

        assertThat(result).contains("\\textbf{platform}");
    }

    @Test
    void doesNotDoubleEscapeAlreadyEscapedCharacters() {
        assertThat(SuggestionSplicer.escapeLatex("already \\% escaped")).isEqualTo("already \\% escaped");
        assertThat(SuggestionSplicer.escapeLatex("bare % sign")).isEqualTo("bare \\% sign");
    }

    @Test
    void summariesGoIntoProseRatherThanIntoTheItemList() {
        String result = SuggestionSplicer.splice(
                MARKDOWN_BODY, ResumeFormat.MARKDOWN, SuggestionKind.SUMMARY, "Experience",
                "Platform engineer with a decade of experience.");

        assertThat(result).contains("Platform engineer with a decade of experience.");
        assertThat(result).doesNotContain("- Platform engineer");
    }

    @Test
    void blankContentIsANoOp() {
        assertThat(SuggestionSplicer.splice(
                MARKDOWN_BODY, ResumeFormat.MARKDOWN, SuggestionKind.SKILL, "Skills", "  "))
                .isEqualTo(MARKDOWN_BODY);
    }

    @Test
    void appliesTwoSuggestionsIndependently() {
        String once = SuggestionSplicer.splice(
                MARKDOWN_BODY, ResumeFormat.MARKDOWN, SuggestionKind.SKILL, "Skills", "Terraform");
        String twice = SuggestionSplicer.splice(
                once, ResumeFormat.MARKDOWN, SuggestionKind.SKILL, "Skills", "Kubernetes");

        assertThat(twice).contains("Terraform").contains("Kubernetes").contains("Java");
    }
}
