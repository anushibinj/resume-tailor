package com.resumetailor.tailoring;

import java.util.List;

/**
 * What the tailoring call returned (LLM call 2).
 *
 * <p>No suggestions here: anything the posting wants that the resume does not show comes
 * from the gap analysis, which judges the finished rewrite rather than predicting it.
 */
public record TailorOutput(String tailoredBody, List<ChangeItem> changes) {

    public record ChangeItem(String section, String changeType, String rationale) {
    }
}
