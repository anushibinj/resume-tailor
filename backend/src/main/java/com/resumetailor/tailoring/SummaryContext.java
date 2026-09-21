package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;

import java.util.UUID;

/**
 * What writing the summary options needs, read once under a transaction. The tailored body
 * is the model's pristine rewrite: the summary is located in it, and a chosen variant is
 * swapped in later, so it must never be a body that already has a variant applied.
 */
public record SummaryContext(
        UUID runId,
        UUID jobDescriptionId,
        UUID llmProfileId,
        ResumeFormat format,
        String originalBody,
        String tailoredBody) {
}
