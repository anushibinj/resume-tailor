package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;

import java.util.UUID;

/**
 * Everything the async pipeline needs, read once under a transaction so the worker
 * thread never touches a detached entity.
 */
public record RunContext(
        UUID runId,
        UUID jobDescriptionId,
        UUID llmProfileId,
        ResumeFormat format,
        String originalBody,
        String preamble,
        String documentTail) {
}
