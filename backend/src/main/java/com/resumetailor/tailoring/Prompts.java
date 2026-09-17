package com.resumetailor.tailoring;

import com.resumetailor.jd.JobDescription;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.resume.ResumeFormat;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The prompt contract for the three LLM calls: extract the posting's requirements,
 * rewrite the resume, then judge which requirements the rewrite covers.
 *
 * <p>The rewrite may reorder, reword, re-emphasise and trim, but never invent. Adding
 * something the resume does not show is a separate, explicit act by the user: the gap
 * analysis offers it, and nothing enters the document until they accept it.
 */
public final class Prompts {

    /**
     * Bumped whenever {@link #JD_ANALYSIS_SYSTEM} changes in a way that makes older
     * cached extractions wrong; {@code JdAnalyzer} re-extracts anything older.
     */
    public static final int JD_ANALYSIS_VERSION = 2;

    private Prompts() {
    }

    public static final String JD_ANALYSIS_SYSTEM = """
            You extract the requirements a job posting screens candidates on.

            Return ONLY a JSON object, no prose and no code fences, shaped exactly like this:
            {
              "company": "string or null",
              "role": "string or null",
              "keywords": [{"keyword": "string", "importance": "REQUIRED" | "PREFERRED" | "NICE"}],
              "responsibilities": ["string"],
              "mustHaves": ["string"]
            }

            Rules for "keywords". Produce between 10 and 25 of them:
            - Each one is a skill, technology, tool, platform or methodology an applicant
              tracking system would scan a resume for.
            - Write the term exactly as it would appear on a resume: "Java", "Kubernetes",
              "CI/CD", "microservices". One to three words.
            - NEVER add a qualifier or gloss. Write "Java", not "Java (Programming Language)"
              or "Java 17+ experience". Strip anything in brackets or parentheses.
            - Do not turn a responsibility into a keyword. "Optimising application
              performance" is a responsibility; the keyword is "performance optimization".
            - Omit anything that is not a skill: locations and offices, visa or travel terms,
              salary and benefits, company descriptions, years of experience, degrees.
            - Omit soft skills and filler ("team player", "fast-paced environment",
              "emerging technologies", "software development processes").
            - No duplicates and no near-duplicates: pick one of "CI/CD", "continuous
              integration", "continuous deployment".

            importance is REQUIRED when the posting states it as a requirement, PREFERRED when
            it is listed as preferred / nice-to-have / a bonus, and NICE otherwise.

            "responsibilities" and "mustHaves" are short paraphrased bullets, at most 8 each.
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

                If the job wants something the resume does not support, leave it out. A separate
                step offers it to the candidate, who decides whether to add it.

                Return ONLY a JSON object, no prose and no code fences, shaped exactly like this:
                {
                  "tailoredBody": "the complete rewritten body, as a single string",
                  "changes": [
                    {"section": "string", "changeType": "REWORDED" | "REORDERED" | "TRIMMED" | "EMPHASISED",
                     "rationale": "one sentence on why this helps for this job"}
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

    /**
     * Judges which requirements the resume covers, and how to add the ones it doesn't.
     *
     * <p>Two things this prompt must get right. Coverage is judged by meaning, because
     * literal matching is what made a Java developer's resume read as missing "Java". And
     * an addition is expressed as an anchor plus text to insert beside it, so it lands
     * inside whatever structure the template uses and can only ever insert, never delete.
     */
    public static String gapAnalysisSystem(ResumeFormat format) {
        String examples = format == ResumeFormat.LATEX
                ? """
                - Comma list:  anchor "Docker, Kubernetes"          insert ", Go"    AFTER
                - Item list:   anchor "\\\\item Built the billing service"  insert "\\n\\\\item Built services in Go"  AFTER
                - Tag macro:   anchor "\\\\cvtag{Java}"                insert "\\n\\\\cvtag{Go}"  AFTER
                Copy the anchor EXACTLY as it appears, including LaTeX escapes such as \\\\& and \\\\%%.
                Never anchor to a commented-out line (one starting with %%) -- it would not appear
                in the PDF. Write the insert in the same macros the surrounding text uses.
                """
                : """
                - Comma list:  anchor "Docker, Kubernetes"   insert ", Go"   AFTER
                - Bullet list: anchor "- Built the billing service"  insert "\\n- Built services in Go"  AFTER
                Write the insert using the same list markers and heading levels as the surrounding text.
                """;

        return """
                You check a tailored resume against a job posting's requirements, and for each
                requirement the resume does not cover you write the exact edit that would add it.

                PART 1 -- COVERAGE. For each requirement, decide whether this resume would
                satisfy a recruiter looking for it. Judge by MEANING, not by string matching:
                - A specific technology covers the general skill it belongs to. "Spring Boot
                  microservices in Java" covers "Java", "backend development" and "microservices".
                - Equivalents and synonyms count. "Jenkins pipelines" covers "CI/CD". "Led a team
                  of six" covers "team leadership". "GCP" covers "Google Cloud".
                - Seniority and scale stated anywhere in the resume count for requirements about them.
                - Set "covered": true only when you can quote the resume text that shows it.
                  Put that quote, copied verbatim and under 120 characters, in "evidence".
                - If the resume never shows it, "covered": false. Do not guess.

                PART 2 -- ADDITIONS. For EVERY requirement with "covered": false, give an
                "addition" that would put it in the resume. Do this for every one of them, even
                when nothing in the resume suggests the candidate has that skill: the candidate
                decides what to keep, not you. Never refuse an addition and never leave it out.

                An addition is anchored, so it can only insert text:
                  "anchor"    text copied EXACTLY from the resume to attach to
                  "placement" "AFTER" or "BEFORE" that anchor
                  "insert"    the exact markup to insert there
                  "label"     short human-readable name of what is being added, e.g. "Go"
                  "kind"      "SKILL" for a skills list, "BULLET" for a bullet, "SUMMARY" for prose
                  "section"   the section it belongs in

                %s
                Prefer extending a list the resume already has, shortest edit that works. Keep the
                phrasing consistent with the resume's voice. If no safe anchor exists, omit
                "anchor" and give "label", "kind" and "section" only.

                The candidate may have already chosen additions of their own; they are listed
                below if so. Those are NOT part of the resume text you are judging. If one of them
                would satisfy a requirement, set "covered": false and "addressedBy" to that
                addition's id instead of writing a new one.

                Return ONLY a JSON object, no prose and no code fences:
                {
                  "requirements": [
                    {"keyword": "<exactly as given to you>",
                     "covered": true,
                     "evidence": "verbatim quote from the resume"},
                    {"keyword": "<exactly as given to you>",
                     "covered": false,
                     "addition": {"label": "Go", "kind": "SKILL", "section": "Technical Skills",
                                  "anchor": "Docker, Kubernetes", "placement": "AFTER", "insert": ", Go"}}
                  ]
                }

                Include every requirement you were given, exactly once, using its exact wording.
                """.formatted(examples);
    }

    public static String gapAnalysisUser(List<JdKeyword> requirements, String resumeBody,
                                         List<ExistingAddition> alreadyAdded) {
        String list = requirements.stream()
                .map(k -> "- " + k.keyword() + " (" + k.importance() + ")")
                .collect(Collectors.joining("\n"));
        String added = alreadyAdded.isEmpty()
                ? "(none)"
                : alreadyAdded.stream()
                        .map(a -> "- id " + a.id() + ": " + a.content())
                        .collect(Collectors.joining("\n"));

        return """
                REQUIREMENTS TO CHECK:
                %s

                ADDITIONS THE CANDIDATE HAS ALREADY CHOSEN (not present in the resume below):
                %s

                RESUME:
                %s
                """.formatted(list.isBlank() ? "(none)" : list, added, resumeBody);
    }

    /** An accepted addition, offered to the model so it can point at one instead of duplicating it. */
    public record ExistingAddition(String id, String content) {
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
