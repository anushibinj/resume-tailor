package com.resumetailor.resume;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a resume body into sections so the UI can diff section-by-section rather than
 * showing one wall of text.
 *
 * <p>This is deliberately a heuristic, not a LaTeX parser. Nothing downstream depends
 * on it being exhaustive: an unrecognised macro simply means a bigger section, never a
 * corrupted document, because segmentation is only ever used for display and for
 * locating where an accepted suggestion should be inserted.
 */
public final class SectionSegmenter {

    /** Sectioning macros used by the common resume templates, e.g. {@code \section{Experience}}. */
    private static final Pattern LATEX_MACRO = Pattern.compile(
            "\\\\(section|subsection|cvsection|cvSection|resumeSection|resheading|sectionTitle)\\*?\\s*\\{");

    /** Environment-style sections, e.g. {@code \begin{rSection}{Experience}}. */
    private static final Pattern LATEX_ENVIRONMENT = Pattern.compile(
            "\\\\begin\\s*\\{(rSection|cvsection|resumeSection)}\\s*\\{");

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^(#{1,6})[ \\t]+(.+?)[ \\t]*$");

    private SectionSegmenter() {
    }

    public static List<ResumeSection> segment(String body, ResumeFormat format) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        List<Heading> headings = format == ResumeFormat.LATEX
                ? latexHeadings(body)
                : markdownHeadings(body);
        return toSections(body, headings);
    }

    private static List<Heading> latexHeadings(String body) {
        List<Heading> headings = new ArrayList<>();
        collect(body, LATEX_MACRO, headings, 1);
        collect(body, LATEX_ENVIRONMENT, headings, 1);
        headings.sort((a, b) -> Integer.compare(a.start, b.start));
        return headings;
    }

    private static void collect(String body, Pattern pattern, List<Heading> into, int level) {
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            // matcher.end() - 1 is the '{' that opens the title argument.
            int open = matcher.end() - 1;
            int close = findMatchingBrace(body, open);
            if (close < 0) {
                continue;
            }
            String title = body.substring(open + 1, close).trim();
            // \subsection is one level deeper than \section.
            int actualLevel = matcher.group().contains("subsection") ? level + 1 : level;
            into.add(new Heading(matcher.start(), stripLatexMarkup(title), actualLevel));
        }
    }

    private static List<Heading> markdownHeadings(String body) {
        List<Heading> headings = new ArrayList<>();
        Matcher matcher = MARKDOWN_HEADING.matcher(body);
        while (matcher.find()) {
            headings.add(new Heading(matcher.start(), matcher.group(2).trim(), matcher.group(1).length()));
        }
        return headings;
    }

    private static List<ResumeSection> toSections(String body, List<Heading> headings) {
        List<ResumeSection> sections = new ArrayList<>();
        if (headings.isEmpty()) {
            sections.add(new ResumeSection(null, 0, 0, body.length(), body));
            return sections;
        }
        // Content before the first heading (name, contact details) is its own section.
        if (headings.get(0).start > 0) {
            String content = body.substring(0, headings.get(0).start);
            if (!content.isBlank()) {
                sections.add(new ResumeSection(null, 0, 0, headings.get(0).start, content));
            }
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int end = i + 1 < headings.size() ? headings.get(i + 1).start : body.length();
            sections.add(new ResumeSection(
                    heading.title, heading.level, heading.start, end, body.substring(heading.start, end)));
        }
        return sections;
    }

    /**
     * Returns the index of the {@code }} matching the {@code {} at {@code openIndex},
     * or -1 when unbalanced. Escaped braces ({@code \{}) are skipped.
     */
    static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Turns {@code \textbf{Experience}} into {@code Experience} for display purposes. */
    private static String stripLatexMarkup(String title) {
        return title.replaceAll("\\\\[a-zA-Z]+\\*?\\s*", "")
                .replace("{", "")
                .replace("}", "")
                .trim();
    }

    private record Heading(int start, String title, int level) {
    }
}
