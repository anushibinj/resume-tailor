package com.resumetailor.tailoring;

import com.resumetailor.keyword.JdKeyword;

import java.util.List;

/** Structured requirements extracted from a job description (LLM call 1). */
public record JdAnalysisResult(
        String company,
        String role,
        List<JdKeyword> keywords,
        List<String> responsibilities,
        List<String> mustHaves,
        String modelUsed) {
}
