package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryVariantsTest {

    private static final String SUMMARY = "Lead engineer with ten years building Java services.";
    private static final String BODY = "\\section{Summary}\n" + SUMMARY + "\n\\section{Skills}\nJava, Kafka\n";

    private static final List<SummaryOptions.Variant> VARIANTS = List.of(
            new SummaryOptions.Variant(4, "Lead Java engineer."),
            new SummaryOptions.Variant(5, "Lead engineer building Java services for ten years."),
            new SummaryOptions.Variant(7, "A much longer version."));

    private static TailoringRun run(Integer selected) {
        TailoringRun run = new TailoringRun();
        run.setFormat(ResumeFormat.LATEX);
        run.setTailoredBody(BODY);
        run.setSummaryOriginal(SUMMARY);
        run.setSummaryVariants(SummaryVariants.toJson(VARIANTS));
        run.setSummaryLines(selected);
        return run;
    }

    @Test
    void variantsSurviveTheRoundTripThroughTheColumn() {
        assertThat(SummaryVariants.fromJson(SummaryVariants.toJson(VARIANTS))).isEqualTo(VARIANTS);
    }

    @Test
    void anUnreadableColumnMeansNoOptionsRatherThanABrokenRun() {
        assertThat(SummaryVariants.fromJson(null)).isEmpty();
        assertThat(SummaryVariants.fromJson("  ")).isEmpty();
        assertThat(SummaryVariants.fromJson("not json")).isEmpty();
    }

    @Test
    void swapsTheChosenLengthInPlaceAndTouchesNothingElse() {
        String body = SummaryVariants.apply(BODY, run(4));

        assertThat(body).isEqualTo("\\section{Summary}\nLead Java engineer.\n\\section{Skills}\nJava, Kafka\n");
    }

    @Test
    void leavesTheRewriteAloneWhenNoLengthIsChosen() {
        assertThat(SummaryVariants.apply(BODY, run(null))).isEqualTo(BODY);
    }

    @Test
    void leavesTheRewriteAloneWhenTheChosenLengthHasNoVariant() {
        assertThat(SummaryVariants.apply(BODY, run(6))).isEqualTo(BODY);
    }

    @Test
    void neverReplacesACommentedOutCopyOfTheSummary() {
        String body = "% " + SUMMARY + "\n\\section{Summary}\n" + SUMMARY + "\n";
        TailoringRun run = run(4);
        run.setTailoredBody(body);

        assertThat(SummaryVariants.apply(body, run))
                .isEqualTo("% " + SUMMARY + "\n\\section{Summary}\nLead Java engineer.\n");
    }

    @Test
    void defaultsToFiveLinesElseTheClosestOffered() {
        assertThat(SummaryVariants.defaultSelection(VARIANTS)).isEqualTo(5);
        assertThat(SummaryVariants.defaultSelection(List.of(
                new SummaryOptions.Variant(6, "a"), new SummaryOptions.Variant(7, "b")))).isEqualTo(6);
        // Equidistant from 5: the shorter wins, because the point of the slider is shorter summaries.
        assertThat(SummaryVariants.defaultSelection(List.of(
                new SummaryOptions.Variant(4, "a"), new SummaryOptions.Variant(6, "b")))).isEqualTo(4);
        assertThat(SummaryVariants.defaultSelection(List.of())).isNull();
    }

    @Test
    void anAcceptedAdditionSurvivesTheSummaryItWasAnchoredInBeingReplaced() {
        // Accepted while the 7-line summary was showing, anchored inside it.
        RunSuggestion addition = new RunSuggestion();
        addition.setKind(SuggestionKind.SKILL);
        addition.setTargetSection("Skills");
        addition.setContent("Go");
        addition.setAnchor("building Java services");
        addition.setPlacement(Placement.AFTER);
        addition.setInsertText(" and Go");
        addition.setStatus(SuggestionStatus.ACCEPTED);

        TailoringRun run = run(4);
        String body = EffectiveBody.compose(run, List.of(addition));

        // The anchor is gone with the old summary, so it falls back to the skills section
        // rather than vanishing while the UI still says it was added.
        assertThat(body).contains("Lead Java engineer.");
        assertThat(body).contains("Go");
    }

    @Test
    void anAdditionAnchoredOutsideTheSummaryLandsExactlyWhereItAlwaysDid() {
        RunSuggestion addition = new RunSuggestion();
        addition.setKind(SuggestionKind.SKILL);
        addition.setContent("Go");
        addition.setAnchor("Java, Kafka");
        addition.setPlacement(Placement.AFTER);
        addition.setInsertText(", Go");
        addition.setStatus(SuggestionStatus.ACCEPTED);

        assertThat(EffectiveBody.compose(run(4), List.of(addition)))
                .contains("Lead Java engineer.")
                .contains("Java, Kafka, Go");
    }
}
