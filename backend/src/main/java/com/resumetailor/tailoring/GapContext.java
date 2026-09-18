package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;

import java.util.List;
import java.util.UUID;

/**
 * What the gap analysis needs, read once under a transaction.
 *
 * <p>{@code body} is the document as it currently stands -- the model's rewrite plus any
 * additions the user has already accepted -- so a re-check judges what they would actually
 * send, and {@code alreadyAdded} lets the model point at one of those instead of
 * proposing it again.
 */
public record GapContext(
        UUID runId,
        UUID jobDescriptionId,
        UUID llmProfileId,
        ResumeFormat format,
        String body,
        List<Prompts.ExistingAddition> alreadyAdded) {
}
