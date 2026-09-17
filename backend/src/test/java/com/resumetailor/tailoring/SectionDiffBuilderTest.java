package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.tailoring.TailoringDtos.SectionDiff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SectionDiffBuilderTest {

    private static RunChange change(String section, String type, String rationale) {
        RunChange runChange = new RunChange();
        runChange.setSectionTitle(section);
        runChange.setChangeType(type);
        runChange.setRationale(rationale);
        runChange.setOrdinal(0);
        return runChange;
    }

    @Test
    void pairsSectionsByTitleAndAttachesRationale() {
        String original = "## Experience\n- Built billing\n\n## Skills\n- Java\n";
        String tailored = "## Experience\n- Shipped billing at scale\n\n## Skills\n- Java\n";

        List<SectionDiff> diffs = SectionDiffBuilder.build(original, tailored, ResumeFormat.MARKDOWN,
                List.of(change("Experience", "REWORDED", "Emphasises scale, which the JD asks for")));

        assertThat(diffs).hasSize(2);
        assertThat(diffs.get(0).title()).isEqualTo("Experience");
        assertThat(diffs.get(0).originalContent()).contains("Built billing");
        assertThat(diffs.get(0).tailoredContent()).contains("Shipped billing at scale");
        assertThat(diffs.get(0).changeType()).isEqualTo("REWORDED");
        assertThat(diffs.get(0).rationale()).contains("JD");
    }

    @Test
    void ordersByTheTailoredDocumentSoReorderingReadsNaturally() {
        String original = "## Education\n- BSc\n\n## Experience\n- Built billing\n";
        String tailored = "## Experience\n- Built billing\n\n## Education\n- BSc\n";

        List<SectionDiff> diffs =
                SectionDiffBuilder.build(original, tailored, ResumeFormat.MARKDOWN, List.of());

        assertThat(diffs).extracting(SectionDiff::title).containsExactly("Experience", "Education");
    }

    @Test
    void makesADroppedSectionVisibleInsteadOfSilentlyOmittingIt() {
        String original = "## Experience\n- Built billing\n\n## Hobbies\n- Chess\n";
        String tailored = "## Experience\n- Built billing\n";

        List<SectionDiff> diffs =
                SectionDiffBuilder.build(original, tailored, ResumeFormat.MARKDOWN, List.of());

        assertThat(diffs).extracting(SectionDiff::title).contains("Hobbies");
        SectionDiff dropped = diffs.stream().filter(d -> d.title().equals("Hobbies")).findFirst().orElseThrow();
        assertThat(dropped.tailoredContent()).isEmpty();
        assertThat(dropped.changeType()).isEqualTo("TRIMMED");
    }

    @Test
    void showsAnAddedSectionWithAnEmptyOriginalSide() {
        String original = "## Experience\n- Built billing\n";
        String tailored = "## Summary\n- Platform engineer\n\n## Experience\n- Built billing\n";

        List<SectionDiff> diffs =
                SectionDiffBuilder.build(original, tailored, ResumeFormat.MARKDOWN, List.of());

        SectionDiff added = diffs.get(0);
        assertThat(added.title()).isEqualTo("Summary");
        assertThat(added.originalContent()).isEmpty();
    }

    @Test
    void showsTheOriginalAloneWhileARunIsStillInFlight() {
        String original = "## Experience\n- Built billing\n";

        List<SectionDiff> diffs =
                SectionDiffBuilder.build(original, null, ResumeFormat.MARKDOWN, List.of());

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).tailoredContent()).isEmpty();
        assertThat(diffs.get(0).originalContent()).contains("Built billing");
    }
}
