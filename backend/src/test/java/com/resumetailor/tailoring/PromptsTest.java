package com.resumetailor.tailoring;

import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.resume.ResumeFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompts are the only thing stopping the rewrite from turning a Java developer into a
 * C++ one, so the rules that do that are pinned here rather than left to a stray edit.
 */
class PromptsTest {

    @Test
    void rewriteMayNotChangeTheCandidatesTitleOrGrowASkillList() {
        for (ResumeFormat format : ResumeFormat.values()) {
            String system = Prompts.tailoringSystem(format);
            assertThat(system).contains("NOT the candidate's title");
            assertThat(system).contains("NEVER gain an item");
        }
    }

    @Test
    void rewriteIsToldTheTargetRoleIsNotTheCandidatesOwn() {
        JdAnalysisResult analysis = new JdAnalysisResult(
                "Microsoft", "Principal Software Engineer",
                List.of(new JdKeyword("C++", KeywordImportance.REQUIRED)),
                List.of(), List.of(), null);

        String user = Prompts.tailoringUser(analysis, "body");

        assertThat(user).contains("NOT the candidate's own title");
        assertThat(user).contains("ALREADY shows");
    }

    @Test
    void coverageIsJudgedOnTheOriginalAndAnchorsComeFromTheRewrite() {
        String user = Prompts.gapAnalysisUser(
                List.of(new JdKeyword("C++", KeywordImportance.REQUIRED)), List.of(),
                "ORIGINAL-TEXT", "REWRITE-TEXT", List.of());

        assertThat(user).contains("judge coverage from this):\nORIGINAL-TEXT");
        assertThat(user).contains("copy anchors from this):\nREWRITE-TEXT");
        assertThat(Prompts.gapAnalysisSystem(ResumeFormat.LATEX))
                .contains("Coverage is judged on the ORIGINAL only");
    }

    @Test
    void onlyAsksTheModelToExplainSkillsTheSharedGlossaryLacks() {
        JdKeyword known = new JdKeyword("Java", KeywordImportance.REQUIRED);
        JdKeyword unknown = new JdKeyword("cloud-native design patterns", KeywordImportance.PREFERRED);

        String user = Prompts.gapAnalysisUser(List.of(known, unknown), List.of(unknown), "o", "t", List.of());

        assertThat(user).contains("NEEDS A DESCRIPTION");
        assertThat(user).contains("\"cloud-native design patterns\"\n");
        assertThat(user).doesNotContain("NEEDS A DESCRIPTION (give each of these a \"description\", and no others):\n"
                + "                \"Java\"");

        String none = Prompts.gapAnalysisUser(List.of(known), List.of(), "o", "t", List.of());
        assertThat(none).contains("do not include any \"description\"");
    }

    @Test
    void descriptionsAreAboutTheTermNotTheCandidate() {
        assertThat(Prompts.gapAnalysisSystem(ResumeFormat.LATEX))
                .contains("Say nothing about the candidate");
    }

    @Test
    void summaryOptionsAreWrittenFromTheOriginalNeverFromTheJobsWishList() {
        for (ResumeFormat format : ResumeFormat.values()) {
            String system = Prompts.summaryVariantsSystem(format, 90);
            assertThat(system).contains("NEVER invent facts");
            assertThat(system).contains("is not their title");
            assertThat(system).contains("does not appear, however much the job asks for it");
        }

        JdAnalysisResult analysis = new JdAnalysisResult(
                "Microsoft", "Principal Software Engineer", List.of(), List.of(), List.of("Own the platform"), null);
        String user = Prompts.summaryVariantsUser(analysis, "ORIGINAL-TEXT", "REWRITE-TEXT");

        assertThat(user).contains("the only source of facts):\nORIGINAL-TEXT");
        assertThat(user).contains("find the summary here and copy it exactly):\nREWRITE-TEXT");
        assertThat(user).contains("NOT the candidate's own title");
    }

    @Test
    void summaryLengthsBecomeACharacterBudgetFromTheConfiguredLineWidth() {
        String system = Prompts.summaryVariantsSystem(ResumeFormat.MARKDOWN, 100);

        assertThat(system).contains("4 lines: 350 to 400 characters");
        assertThat(system).contains("7 lines: 650 to 700 characters");
        assertThat(system).contains("about 100 visible");
    }
}
