package com.resumetailor.tailoring;

import com.resumetailor.jd.JobDescription;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.resume.ResumeFormat;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The prompt contract for the LLM calls: extract the posting's requirements, rewrite the
 * resume, write the summary at several lengths, then judge which requirements the resume
 * covers.
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
                7. The candidate's identity stays theirs. The headline, tagline or title line at
                   the top of the resume keeps the candidate's OWN title and the stack they list
                   there. The role the job is hiring for is NOT the candidate's title: never write
                   it (or a more senior version of it) as their headline or job title.
                8. Lists of skills or technologies -- in a headline, tagline, summary or skills
                   section -- may be reordered or shortened to lead with what this job cares about.
                   They may NEVER gain an item. If the job asks for C++ and the resume does not
                   already say C++, C++ does not appear anywhere in your output. A Java developer
                   stays a Java developer however long the job's keyword list is.
                9. Leave comment lines (LaTeX lines starting with %%) exactly as they are. Do not
                   delete them and do not uncomment them.

                If the job wants something the resume does not support, leave it out. A separate
                step shows it to the candidate as a gap and offers to add it; the candidate, not
                you, decides whether it goes in.

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
                THE JOB BEING APPLIED FOR (this is NOT the candidate's own title): %s at %s

                WHAT THIS JOB REQUIRES:
                %s

                KEY RESPONSIBILITIES:
                %s

                KEYWORDS THE POSTING USES. For reference only. Use one only where the resume
                ALREADY shows that skill; never add one to a headline, tagline or skills list just
                because the posting lists it. The ones the resume lacks are handled separately.
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
     * Writes the rewrite's summary again at each length the slider offers.
     *
     * <p>Shorter is not a licence to invent and longer is not either: the facts come from the
     * ORIGINAL resume only, which is why the original is sent alongside the rewrite. The
     * summary is quoted back verbatim so Java can find it and swap a variant in; if it cannot
     * be found the options are rejected rather than applied somewhere guessed.
     *
     * @param charsPerLine visible characters in one rendered line, which is what turns "N
     *                     lines" into a length the model can aim for
     */
    public static String summaryVariantsSystem(ResumeFormat format, int charsPerLine) {
        String dialect = format == ResumeFormat.LATEX ? "LaTeX" : "Markdown";
        String dialectRule = format == ResumeFormat.LATEX
                ? """
                - Use ONLY macros that already appear in the summary or elsewhere in the resume.
                  Keep every brace balanced and copy escapes such as \\& and \\% exactly.
                - Never emit a line starting with %, and never a section heading or environment.
                """
                : """
                - Use the same emphasis style the resume already uses. No headings and no list
                  markers unless the summary itself is a list.
                """;
        StringBuilder budgets = new StringBuilder();
        for (int lines = SummaryVariants.MIN_LINES; lines <= SummaryVariants.MAX_LINES; lines++) {
            budgets.append("  - ").append(lines).append(" lines: ")
                    .append(Math.round((lines - 0.5) * charsPerLine)).append(" to ")
                    .append(lines * charsPerLine).append(" characters\n");
        }

        return """
                You write one professional summary at several different lengths.

                You are given the candidate's ORIGINAL resume, which is the only source of facts,
                and a TAILORED version rewritten for one job.

                STEP 1 -- FIND THE SUMMARY in the TAILORED resume. It is the paragraph of prose
                that sums the candidate up, usually near the top and headed Summary, Profile, About
                or Objective, though it may have no heading. It is NOT the headline, tagline or
                title line, NOT a skills list and NOT an experience bullet. Copy it into "original"
                exactly as it appears in the TAILORED resume: same %s markup, same characters, same
                line breaks, without the section heading. If the resume has no such paragraph, set
                "original" to null and "variants" to [].

                STEP 2 -- WRITE THE SUMMARY at each of these lengths. A line holds about %d visible
                characters; markup that does not print, such as a macro name or a brace, does not
                count. Stay inside each range:
                %s
                Rules. Breaking any of these makes the output unusable:
                1. NEVER invent facts. Use only what the ORIGINAL resume shows: no new employer,
                   job title, date, degree, certification, tool, technology or metric, and no
                   inflated number. A longer variant adds detail the resume already has, never
                   detail it lacks.
                2. The candidate stays who they are. Keep their OWN title and the stack they list.
                   The role the job is hiring for is not their title. A skill the resume does not
                   mention does not appear, however much the job asks for it.
                3. Every variant is a complete, natural paragraph in its own right, not the longer
                   one cut off. The shorter it is, the more it keeps only what matters most for
                   this job. Keep the candidate's voice and person; do not switch to "I" or "he".
                4. Preserve the %s markup dialect exactly.
                %s
                Return ONLY a JSON object, no prose and no code fences, shaped exactly like this:
                {
                  "original": "the summary exactly as it appears in the TAILORED resume, or null",
                  "variants": [
                    {"lines": 4, "text": "..."},
                    {"lines": 5, "text": "..."},
                    {"lines": 6, "text": "..."},
                    {"lines": 7, "text": "..."}
                  ]
                }
                Include one variant for every length listed, and nothing else.
                """.formatted(dialect, charsPerLine, budgets, dialect, dialectRule);
    }

    public static String summaryVariantsUser(JdAnalysisResult analysis, String originalBody, String tailoredBody) {
        return """
                THE JOB BEING APPLIED FOR (this is NOT the candidate's own title): %s at %s

                WHAT THIS JOB REQUIRES:
                %s

                ORIGINAL RESUME (the only source of facts):
                %s

                TAILORED RESUME (find the summary here and copy it exactly):
                %s
                """.formatted(
                orDash(analysis.role()),
                orDash(analysis.company()),
                bullets(analysis.mustHaves()),
                originalBody,
                tailoredBody);
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
                You check a candidate's resume against a job posting's requirements, and for each
                requirement it does not cover you write the exact edit that would add it.

                You are given two texts: the candidate's ORIGINAL resume, and a TAILORED version
                of it that was rewritten for this job. Coverage is judged on the ORIGINAL only.
                It is what the candidate genuinely has. The rewrite is meant to only reorder and
                reword it, but it can slip and add skills or titles the candidate never had; such
                text is never evidence. The TAILORED text is only where additions are anchored.

                PART 1 -- COVERAGE. For each requirement, decide whether the ORIGINAL resume would
                satisfy a recruiter looking for it. Judge by MEANING, not by string matching:
                - A specific technology covers the general skill it belongs to. "Spring Boot
                  microservices in Java" covers "Java", "backend development" and "microservices".
                - Equivalents and synonyms count. "Jenkins pipelines" covers "CI/CD". "Led a team
                  of six" covers "team leadership". "GCP" covers "Google Cloud".
                - Seniority and scale stated anywhere in the resume count for requirements about them.
                - Set "covered": true only when you can quote the ORIGINAL resume text that shows
                  it. Put that quote, copied verbatim and under 120 characters, in "evidence".
                - If the original never shows it, "covered": false, even when the tailored text
                  mentions it. Do not guess.

                PART 2 -- ADDITIONS. For EVERY requirement with "covered": false, give an
                "addition" that would put it in the resume. Do this for every one of them, even
                when nothing in the resume suggests the candidate has that skill: the candidate
                decides what to keep, not you. Never refuse an addition and never leave it out.

                An addition is anchored, so it can only insert text:
                  "anchor"    text copied EXACTLY from the TAILORED resume to attach to
                  "placement" "AFTER" or "BEFORE" that anchor
                  "insert"    the exact markup to insert there
                  "label"     short human-readable name of what is being added, e.g. "Go"
                  "kind"      "SKILL" for a skills list, "BULLET" for a bullet, "SUMMARY" for prose
                  "section"   the section it belongs in

                %s
                Prefer extending a list the resume already has, shortest edit that works. Put a
                technology where a reader looks for one: the list in the headline or tagline if it
                has one, otherwise the skills section. Put methodologies and working-style
                requirements ("agile", "team leadership") in the summary if the resume has one.
                Keep the phrasing consistent with the resume's voice. If no safe anchor exists,
                omit "anchor" and give "label", "kind" and "section" only.

                The candidate may have already chosen additions of their own; they are listed
                below if so. Those are NOT part of either resume text below. If one of them
                would satisfy a requirement, set "covered": false and "addressedBy" to that
                addition's id instead of writing a new one.

                PART 3 -- DESCRIPTIONS. The user message lists some requirements under "NEEDS A
                DESCRIPTION". For exactly those, add a "description" to their entry: one or two
                plain sentences, under 45 words, saying what the skill, technology or practice is
                and what it is used for, so someone who has never met the term understands it.
                Describe the term itself, in general. Say nothing about the candidate, their
                resume or this job, and do not just repeat the keyword. Requirements that are not
                on that list must not carry a "description".

                Return ONLY a JSON object, no prose and no code fences:
                {
                  "requirements": [
                    {"keyword": "<exactly the keyword string you were given, nothing appended>",
                     "covered": true,
                     "evidence": "verbatim quote from the ORIGINAL resume",
                     "description": "only when listed under NEEDS A DESCRIPTION"},
                    {"keyword": "<exactly as given to you>",
                     "covered": false,
                     "addition": {"label": "Go", "kind": "SKILL", "section": "Technical Skills",
                                  "anchor": "Docker, Kubernetes", "placement": "AFTER", "insert": ", Go"},
                     "description": "only when listed under NEEDS A DESCRIPTION"}
                  ]
                }

                Include every requirement you were given, exactly once, using its exact wording.
                """.formatted(examples);
    }

    /**
     * @param needDescription the requirements no one has explained yet. Anything already in
     *                        the shared glossary is left out so the model is not paid to
     *                        write it again.
     */
    public static String gapAnalysisUser(List<JdKeyword> requirements, List<JdKeyword> needDescription,
                                         String originalBody, String tailoredBody,
                                         List<ExistingAddition> alreadyAdded) {
        // JSON rather than "- Java (REQUIRED)" lines: asked to echo the keyword exactly,
        // models copied the whole line back, importance included, and nothing matched.
        String list = requirements.stream()
                .map(k -> "  {\"keyword\": \"" + k.keyword().replace("\"", "'") + "\", \"importance\": \""
                        + k.importance() + "\"}")
                .collect(Collectors.joining(",\n"));
        String describe = needDescription.isEmpty()
                ? "(none -- do not include any \"description\")"
                : needDescription.stream()
                        .map(k -> "\"" + k.keyword().replace("\"", "'") + "\"")
                        .collect(Collectors.joining(", "));
        String added = alreadyAdded.isEmpty()
                ? "(none)"
                : alreadyAdded.stream()
                        .map(a -> "- id " + a.id() + ": " + a.content())
                        .collect(Collectors.joining("\n"));

        return """
                REQUIREMENTS TO CHECK. Echo each "keyword" back exactly as written here,
                with nothing added to it:
                [
                %s
                ]

                NEEDS A DESCRIPTION (give each of these a "description", and no others):
                %s

                ADDITIONS THE CANDIDATE HAS ALREADY CHOSEN (not present in either text below):
                %s

                ORIGINAL RESUME (judge coverage from this):
                %s

                TAILORED RESUME (copy anchors from this):
                %s
                """.formatted(list.isBlank() ? "" : list, describe, added, originalBody, tailoredBody);
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
