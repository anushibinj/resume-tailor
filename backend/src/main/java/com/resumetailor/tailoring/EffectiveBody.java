package com.resumetailor.tailoring;

import java.util.List;

/**
 * The document the user sees and exports: the model's pristine rewrite, with the chosen
 * summary length swapped in, plus every addition currently accepted.
 *
 * <p>Order matters. The summary is swapped first and additions applied afterwards, so an
 * accepted addition can never be wiped out by moving the summary slider: if its anchor sat
 * inside the summary that was just replaced, it falls back to section placement rather than
 * disappearing while the UI still calls it accepted.
 */
public final class EffectiveBody {

    private EffectiveBody() {
    }

    /** The rewrite with the chosen summary applied and no additions: what anchors are copied from. */
    public static String withSummary(TailoringRun run) {
        return SummaryVariants.apply(run.getTailoredBody(), run);
    }

    public static String compose(TailoringRun run, List<RunSuggestion> accepted) {
        String body = withSummary(run);
        if (body == null) {
            return "";
        }
        for (RunSuggestion addition : accepted) {
            body = AdditionApplier.apply(body, run.getFormat(), addition);
        }
        return body;
    }
}
