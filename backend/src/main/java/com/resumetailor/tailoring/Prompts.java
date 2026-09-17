package com.resumetailor.tailoring;

import com.resumetailor.jd.JobDescription;
import com.resumetailor.resume.ResumeFormat;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The prompt contract for both LLM calls.
 *
 * <p>The tailoring prompt is the product's core promise: the model may reorder, reword,
 * re-emphasise and trim, but it may never invent. Anything the job wants that the resume
 * does not support goes to {@code suggestions}, where the user decides -- accepting a
 * suggestion is the user asserting a claim is true, which is not a model's call to make.
 */
public final class Prompts {

    private Prompts() {
    }

    public static final String JD_ANALYSIS_SYSTEM = """
            You extract structured hiring requirements from job descriptions.

            Return ONLY a JSON object, no prose and no code fences, shaped exactly like this:
            {
              "company": "string or null",
              "role": "string or null",
              "keywords": [{"keyword": "string", "importance": "REQUIRED" | "PREFERRED" | "NICE"}],
              "responsibilities": ["string"],
              "mustHaves": ["string"]
            }

            Rules:
            - "keywords" are concrete, searchable skills, technologies, tools, platforms or
              methodologies -- the terms an applicant tracking system would scan for.
              Produce between 10 and 25 of them.
            - Each keyword is a single term or a short phrase ("Kubernetes", "CI/CD",
              "distributed systems"), never a sentence.
            - Use the job description's own wording. Do not expand abbreviations and do not
              substitute synonyms.
            - importance is REQUIRED when the posting states it as a requirement, PREFERRED
              when it is listed as preferred / nice-to-have / a bonus, and NICE otherwise.
            - "responsibilities" and "mustHaves" are short paraphrased bullets, at most 8 each.
            - Omit soft skills and generic filler ("team player", "fast-paced environment").
            """;

    public static String jdAnalysisUser(JobDescription jd) {
        StringBuilder sb = new StringBuilder();
        if (jd.getCompany() != null && !jd.getCompany().isBlank()) {
            sb.append("Company (given by the candidate): ").append(jd.getCompany()).append('\n');
        }
        if (jd.getRole() != null && !jd.getRole().isBlank()) {
            sb.append("Role (given by the candidate): ").append(jd.getRole()).append('\n');
        }
        sb.append("\nJOB DESCRIPTION:\n").append(jd.getRawText());
        return sb.toString();
    }

    public static String tailoringSystem(ResumeFormat format) {
        String dialect = format == ResumeFormat.LATEX ? "LaTeX" : "Markdown";
        String dialectRule = format == ResumeFormat.LATEX
                ? """
                - The input is LaTeX BODY content (everything between \\begin{document} and
                  \\end{document}). Emit LaTeX using ONLY macros and environments that already
                  appear in the input. Do not invent new commands, do not add \\usepackage, and
                  do not emit a preamble or \\begin{document} / \\end{document} -- those are
                  added back outside your output and you will never see them.
                - Keep every macro balanced. Unbalanced braces make the document fail to compile.
                """
                : """
                - The input is Markdown. Emit Markdown using the same heading levels, list
                  markers and emphasis style already present in the input.
                """;

        return """
                You are an expert resume editor. You rewrite a candidate's resume so it speaks
                directly to one specific job description.

                ABSOLUTE RULES. Breaking any of these makes your output unusable:
                1. NEVER invent facts. Do not add employers, job titles, dates, degrees,
                   certifications, publications, tools, technologies, or metrics that are not
                   already present in the resume.
                2. NEVER alter employer names, job titles, dates, degrees or institutions.
                3. NEVER inflate numbers. If a bullet says "reduced latency by 20%%", it stays 20%%.
                4. You MAY ONLY: reorder sections and bullets, reword existing bullets, shift
                   emphasis onto the experience this job cares about, trim content irrelevant to
                   this job, and rewrite the summary using facts already in the resume.
                5. Preserve the %s markup dialect exactly.
                %s
                6. Keep the overall structure and roughly the same length. Do not drop a whole
                   section unless it is plainly irrelevant to this role.

                If the job wants something the resume does not support, DO NOT put it in the
                resume. Put it in "suggestions" instead, phrased as a bullet the candidate could
                use IF it is true of them. The candidate reviews every suggestion before it is
                used.

                Return ONLY a JSON object, no prose and no code fences, shaped exactly like this:
                {
                  "tailoredBody": "the complete rewritten body, as a single string",
                  "changes": [
                    {"section": "string", "changeType": "REWORDED" | "REORDERED" | "TRIMMED" | "EMPHASISED",
                     "rationale": "one sentence on why this helps for this job"}
                  ],
                  "suggestions": [
                    {"kind": "BULLET" | "SKILL" | "SUMMARY", "targetSection": "string",
                     "content": "the proposed text", "rationale": "which JD requirement this addresses"}
                  ]
                }

                "tailoredBody" must be the COMPLETE body, not a fragment and not a diff.
                Include every section that should remain, in the order they should appear.
                """.formatted(dialect, dialectRule);
    }

    public static String tailoringUser(JdAnalysisResult analysis, String resumeBody) {
        String keywords = analysis.keywords().stream()
                .map(k -> "- " + k.keyword() + " (" + k.importance() + ")")
                .collect(Collectors.joining("\n"));

        return """
                TARGET ROLE: %s at %s

                WHAT THIS JOB REQUIRES:
                %s

                KEY RESPONSIBILITIES:
                %s

                KEYWORDS THE POSTING USES (weave in the ones the resume genuinely supports):
                %s

                CANDIDATE'S CURRENT RESUME BODY:
                %s
                """.formatted(
                orDash(analysis.role()),
                orDash(analysis.company()),
                bullets(analysis.mustHaves()),
                bullets(analysis.responsibilities()),
                keywords.isBlank() ? "(none extracted)" : keywords,
                resumeBody);
    }

    private static String bullets(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(none listed)";
        }
        return values.stream().map(v -> "- " + v).collect(Collectors.joining("\n"));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "(unspecified)" : value;
    }
}
