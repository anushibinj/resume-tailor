package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionApplierTest {

    /** Shaped like the AltaCV template: skills are comma lists inside a macro argument. */
    private static final String ALTACV = """
            \\cvsection{Technical Skills}

            \\cvachievement{-}{Backend Technologies}{Java, Spring Boot, Microservices, Kafka \\& RabbitMQ}

            \\divider

            \\cvachievement{-}{Cloud \\& DevOps}{AWS, Docker, Kubernetes, Terraform}

            % \\cvsection{Education}
            % \\cvachievement{-}{Languages}{Java, Scala}
            """;

    private static RunSuggestion addition(String label, String anchor, Placement placement, String insert) {
        RunSuggestion suggestion = new RunSuggestion();
        suggestion.setKind(SuggestionKind.SKILL);
        suggestion.setTargetSection("Technical Skills");
        suggestion.setContent(label);
        suggestion.setAnchor(anchor);
        suggestion.setPlacement(placement);
        suggestion.setInsertText(insert);
        suggestion.setStatus(SuggestionStatus.PROPOSED);
        return suggestion;
    }

    @Test
    void extendsACommaListInsideTheTemplatesOwnMacro() {
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX,
                addition("Go", "AWS, Docker, Kubernetes, Terraform", Placement.AFTER, ", Go"));

        assertThat(result).contains("{AWS, Docker, Kubernetes, Terraform, Go}");
        // Everything else is untouched.
        assertThat(result).contains("{Java, Spring Boot, Microservices, Kafka \\& RabbitMQ}");
    }

    @Test
    void placesAnAdditionBeforeItsAnchorWhenAsked() {
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX, addition("Go", "AWS,", Placement.BEFORE, "Go, "));

        assertThat(result).contains("{Go, AWS, Docker");
    }

    @Test
    void neverPlacesAnAdditionIntoACommentedOutBlock() {
        // "Java, Scala" exists only on a commented line; text put there never reaches the PDF.
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX, addition("Go", "Java, Scala", Placement.AFTER, ", Go"));

        assertThat(result).doesNotContain("Java, Scala, Go");
        // Falls back to the section instead of silently vanishing.
        assertThat(result).contains("Go");
    }

    @Test
    void isAdditiveOnly() {
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX,
                addition("Go", "AWS, Docker, Kubernetes, Terraform", Placement.AFTER, ", Go"));

        // Every non-blank line of the original survives verbatim.
        ALTACV.lines().filter(line -> !line.isBlank())
                .forEach(line -> assertThat(result.replace(", Go", "")).contains(line));
        assertThat(result.length()).isGreaterThan(ALTACV.length());
    }

    @Test
    void doesNotDuplicateTheAnchorWhenTheModelRepeatsItInTheInsert() {
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX,
                addition("Go", "AWS, Docker", Placement.AFTER, "AWS, Docker, Go"));

        assertThat(result).contains("{AWS, Docker, Go, Kubernetes, Terraform}");
        assertThat(result).doesNotContain("AWS, DockerAWS");
    }

    @Test
    void findsAnAnchorTheModelReWrapped() {
        String body = "\\cvachievement{-}{Cloud}{AWS,\n    Docker, Kubernetes}";

        String result = AdditionApplier.apply(
                body, ResumeFormat.LATEX, addition("Go", "AWS, Docker, Kubernetes", Placement.AFTER, ", Go"));

        assertThat(result).contains(", Go");
    }

    @Test
    void escapesCharactersThatWouldBreakTheBuild() {
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX,
                addition("R&D", "AWS, Docker, Kubernetes, Terraform", Placement.AFTER, ", R&D 50% C#"));

        assertThat(result).contains(", R\\&D 50\\% C\\#");
    }

    @Test
    void fallsBackToSectionPlacementWhenTheAnchorIsNotInTheResume() {
        String result = AdditionApplier.apply(
                ALTACV, ResumeFormat.LATEX,
                addition("Go", "text that does not exist anywhere", Placement.AFTER, "Go"));

        assertThat(result).contains("Go");
        assertThat(result.indexOf("Go")).isGreaterThan(result.indexOf("Technical Skills"));
    }

    @Test
    void handlesAnAdditionWithNoAnchorAtAll() {
        String markdown = "## Skills\n- Java, Spring Boot\n";
        RunSuggestion suggestion = addition("Go", null, null, null);

        String result = AdditionApplier.apply(markdown, ResumeFormat.MARKDOWN, suggestion);

        assertThat(result).contains("- Go");
    }

    @Test
    void extendsAMarkdownListWithoutTouchingLaTeXEscaping() {
        String markdown = "## Skills\n- Java, Spring Boot, Docker\n";

        String result = AdditionApplier.apply(
                markdown, ResumeFormat.MARKDOWN,
                addition("C#", "Java, Spring Boot, Docker", Placement.AFTER, ", C#"));

        assertThat(result).contains("- Java, Spring Boot, Docker, C#");
        assertThat(result).doesNotContain("C\\#");
    }
}
