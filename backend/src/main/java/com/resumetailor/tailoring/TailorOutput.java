package com.resumetailor.tailoring;

import java.util.List;

/** What the tailoring call returned (LLM call 2), before anything is persisted. */
public record TailorOutput(String tailoredBody, List<ChangeItem> changes, List<SuggestionItem> suggestions) {

    public record ChangeItem(String section, String changeType, String rationale) {
    }

    public record SuggestionItem(SuggestionKind kind, String targetSection, String content, String rationale) {
    }
}
