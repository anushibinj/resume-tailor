package com.resumetailor.resume;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a resume's markup dialect and splits it into preamble / body / tail.
 *
 * <p>Pure functions with no Spring dependencies -- this is the load-bearing logic of
 * the app and is covered directly by unit tests.
 */
public final class ResumeParser {

    /** Any of these means we are certainly looking at a LaTeX document. */
    private static final Pattern LATEX_STRONG =
            Pattern.compile("\\\\(documentclass|begin\\s*\\{document}|usepackage)");

    /** A generic {@code \command} token, used to classify LaTeX fragments. */
    private static final Pattern LATEX_COMMAND = Pattern.compile("\\\\[a-zA-Z]{2,}");

    private static final Pattern BEGIN_DOCUMENT = Pattern.compile("\\\\begin\\s*\\{document}");
    private static final Pattern END_DOCUMENT = Pattern.compile("\\\\end\\s*\\{document}");

    /** Below this many LaTeX commands, an ambiguous document is treated as Markdown. */
    private static final int LATEX_COMMAND_THRESHOLD = 5;

    private ResumeParser() {
    }

    public static ResumeFormat detectFormat(String source) {
        if (source == null || source.isBlank()) {
            return ResumeFormat.MARKDOWN;
        }
        if (LATEX_STRONG.matcher(source).find()) {
            return ResumeFormat.LATEX;
        }
        // A .tex fragment without a preamble still reads as LaTeX if it is dense with commands.
        long commandCount = LATEX_COMMAND.matcher(source).results().count();
        return commandCount >= LATEX_COMMAND_THRESHOLD ? ResumeFormat.LATEX : ResumeFormat.MARKDOWN;
    }

    public static ParsedResume parse(String source) {
        return parse(source, detectFormat(source));
    }

    public static ParsedResume parse(String source, ResumeFormat format) {
        String text = source == null ? "" : source;
        if (format == ResumeFormat.MARKDOWN) {
            return new ParsedResume(ResumeFormat.MARKDOWN, "", text, "");
        }

        Matcher begin = BEGIN_DOCUMENT.matcher(text);
        if (!begin.find()) {
            // A body-only .tex fragment: nothing to protect, treat it all as body.
            return new ParsedResume(ResumeFormat.LATEX, "", text, "");
        }

        int bodyStart = begin.end();
        int bodyEnd = text.length();
        String tail = "";

        Matcher end = END_DOCUMENT.matcher(text);
        int lastEndStart = -1;
        while (end.find()) {
            if (end.start() >= bodyStart) {
                lastEndStart = end.start();
            }
        }
        if (lastEndStart >= 0) {
            bodyEnd = lastEndStart;
            tail = text.substring(lastEndStart);
        }

        return new ParsedResume(
                ResumeFormat.LATEX,
                text.substring(0, bodyStart),
                text.substring(bodyStart, bodyEnd),
                tail);
    }

    /**
     * Joins the three parts back into a document.
     *
     * <p>The seams are normalised so the result does not depend on whether the model
     * emitted stray blank lines, and the file ends with exactly one newline.
     */
    public static String reassemble(String preamble, String body, String documentTail) {
        String pre = preamble == null ? "" : preamble.stripTrailing();
        String mid = body == null ? "" : body.strip();
        String tail = documentTail == null ? "" : documentTail.strip();

        StringBuilder out = new StringBuilder();
        if (!pre.isEmpty()) {
            out.append(pre).append('\n');
        }
        out.append(mid);
        if (!tail.isEmpty()) {
            out.append('\n').append(tail);
        }
        return out.append('\n').toString();
    }
}
