package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;

import java.util.List;
import java.util.UUID;

/**
 * What the gap analysis needs, read once under a transaction.
 *
 * <p>Coverage is judged on {@code originalBody} -- what the user's resume genuinely shows --
 * so a rewrite that slips and adds a skill the candidate never had cannot make a gap read
 * as covered. {@code body} is the model's pristine rewrite, which is only where additions
 * are anchored; {@code alreadyAdded} lets the model point at an addition the user already
 * accepted instead of proposing it again.
 */
public record GapContext(
        UUID runId,
        UUID jobDescriptionId,
        UUID llmProfileId,
        ResumeFormat format,
        String originalBody,
        String body,
        List<Prompts.ExistingAddition> alreadyAdded) {
}
