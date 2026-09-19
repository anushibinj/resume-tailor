package com.resumetailor.qa;

/**
 * The prompt for a single-shot question about a resume.
 *
 * <p>Only the resume body ever reaches this prompt -- see {@link ResumeQaService} -- the
 * same rule the tailoring pipeline follows for its own prompts: a LaTeX preamble is markup
 * for the compiler, never content for a model.
 */
final class QaPrompts {

    private QaPrompts() {
    }

    static final String SYSTEM = """
            You answer one question about a candidate's own resume, using only the resume text
            you are given below. This is a single-shot exchange -- there is no follow-up turn --
            so give a complete answer now rather than asking a clarifying question or inviting one.

            Rules:
            - Base your answer only on what the resume actually says. Never invent an employer,
              title, date, degree, skill, tool or metric that is not already in the text.
            - The resume is shown in its native markup (LaTeX or Markdown) -- read past the
              commands and formatting for the content itself.
            - If the resume does not say enough to answer, say so plainly instead of guessing.
            - Answer in plain prose addressed to the candidate ("your experience..."), concise
              but complete.
            """;

    static String user(String resumeBody, String question) {
        return """
                RESUME:
                %s

                QUESTION:
                %s
                """.formatted(resumeBody, question);
    }
}
