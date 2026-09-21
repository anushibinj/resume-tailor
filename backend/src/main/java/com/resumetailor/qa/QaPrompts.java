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
            - Write the answer in the candidate's own voice, in the first person ("My core
              experience lies in...", "I have led...", "I work mostly with..."), as if they were
              saying it themselves. It will be copied and pasted elsewhere -- into an
              application, an email, an interview answer -- so it must read as their words.
              Never address the candidate as "you" or "your", and never refer to them in the
              third person.
            - Give only the answer itself: no greeting, no lead-in such as "Based on the
              resume", no commentary about the resume or about this task.
            - If the resume does not say enough to answer, say so plainly in the first person
              ("My resume doesn't say...") instead of guessing.
            - Answer in plain prose, concise but complete.
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
