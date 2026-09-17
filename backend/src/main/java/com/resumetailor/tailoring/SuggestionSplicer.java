package com.resumetailor.tailoring;

import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.resume.ResumeSection;
import com.resumetailor.resume.SectionSegmenter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inserts an accepted suggestion into a tailored resume body.
 *
 * <p>Runs only when the user explicitly accepts a suggestion. It aims to put the text
 * where it belongs and, failing that, to append it somewhere visible -- never to
 * silently drop it and never to produce markup that will not compile.
 */
public final class SuggestionSplicer {

    /** List terminators used by the common LaTeX resume templates. */
    private static final Pattern LATEX_LIST_END =
            Pattern.compile("(?m)^[ \\t]*(\\\\end\\s*\\{(itemize|enumerate)}|\\\\resumeItemListEnd)");

    private static final Pattern LATEX_RESUME_ITEM = Pattern.compile("\\\\resumeItem\\s*\\{");
    private static final Pattern LATEX_PLAIN_ITEM = Pattern.compile("(?m)^[ \\t]*\\\\item\\b");
    private static final Pattern MARKDOWN_BULLET = Pattern.compile("(?m)^([ \\t]*)([-*+])[ \\t]+\\S");

    /** Escapes only the characters that would break a compile if left bare. */
    private static final Pattern LATEX_UNESCAPED_SPECIAL = Pattern.compile("(?<!\\\\)([%&#_])");

    private SuggestionSplicer() {
    }

    public static String splice(String body, ResumeFormat format, SuggestionKind kind,
                                String targetSection, String content) {
        if (content == null || content.isBlank()) {
            return body;
        }
        String text = content.strip();
        List<ResumeSection> sections = SectionSegmenter.segment(body, format);
        ResumeSection target = findSection(sections, targetSection);

        if (target == null) {
            return appendAtEnd(body, format, kind, text);
        }
        return insertIntoSection(body, target, format, kind, text);
    }

    /** Exact title match first, then a containment match, so "Skills" finds "Technical Skills". */
    private static ResumeSection findSection(List<ResumeSection> sections, String targetSection) {
        if (targetSection == null || targetSection.isBlank()) {
            return null;
        }
        String needle = targetSection.strip().toLowerCase();
        for (ResumeSection section : sections) {
            if (section.title() != null && section.title().strip().equalsIgnoreCase(needle)) {
                return section;
            }
        }
        for (ResumeSection section : sections) {
            if (section.title() == null) {
                continue;
            }
            String title = section.title().strip().toLowerCase();
            if (title.contains(needle) || needle.contains(title)) {
                return section;
            }
        }
        return null;
    }

    private static String insertIntoSection(String body, ResumeSection section, ResumeFormat format,
                                            SuggestionKind kind, String text) {
        String sectionText = section.content();

        if (format == ResumeFormat.LATEX) {
            Matcher listEnd = lastMatch(LATEX_LIST_END, sectionText);
            if (listEnd != null && kind != SuggestionKind.SUMMARY) {
                String line = latexItemLine(sectionText, text, indentOf(sectionText, listEnd.start()));
                int at = section.startOffset() + listEnd.start();
                return body.substring(0, at) + line + "\n" + body.substring(at);
            }
            return insertAtSectionEnd(body, section, "\n\n" + escapeLatex(text) + "\n");
        }

        Matcher bullet = lastMatch(MARKDOWN_BULLET, sectionText);
        if (bullet != null && kind != SuggestionKind.SUMMARY) {
            int lineEnd = endOfLine(sectionText, bullet.start());
            String line = "\n" + bullet.group(1) + bullet.group(2) + " " + text;
            int at = section.startOffset() + lineEnd;
            return body.substring(0, at) + line + body.substring(at);
        }
        return insertAtSectionEnd(body, section, "\n\n" + text + "\n");
    }

    /** Appends just before the next section starts, preserving the blank line that separates them. */
    private static String insertAtSectionEnd(String body, ResumeSection section, String addition) {
        int at = section.endOffset();
        while (at > section.startOffset() && Character.isWhitespace(body.charAt(at - 1))) {
            at--;
        }
        return body.substring(0, at) + addition + body.substring(at);
    }

    private static String appendAtEnd(String body, ResumeFormat format, SuggestionKind kind, String text) {
        String rendered = format == ResumeFormat.LATEX ? escapeLatex(text) : text;
        String prefix = format == ResumeFormat.MARKDOWN && kind != SuggestionKind.SUMMARY ? "- " : "";
        String trimmed = body.stripTrailing();
        return trimmed + "\n\n" + prefix + rendered + "\n";
    }

    /** Matches the item macro the surrounding section already uses. */
    private static String latexItemLine(String sectionText, String text, String indent) {
        String escaped = escapeLatex(text);
        if (LATEX_RESUME_ITEM.matcher(sectionText).find()) {
            return indent + "  \\resumeItem{" + escaped + "}";
        }
        if (LATEX_PLAIN_ITEM.matcher(sectionText).find()) {
            return indent + "  \\item " + escaped;
        }
        return indent + "  \\item " + escaped;
    }

    /**
     * Escapes the characters that silently break a LaTeX build -- a bare {@code %}
     * comments out the rest of the line. Backslashes and braces are left alone so that
     * intentional markup like {@code \textbf{...}} still works.
     */
    static String escapeLatex(String text) {
        return LATEX_UNESCAPED_SPECIAL.matcher(text).replaceAll("\\\\$1");
    }

    private static Matcher lastMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        Matcher last = null;
        int start = -1;
        while (matcher.find()) {
            start = matcher.start();
        }
        if (start < 0) {
            return null;
        }
        // Re-run to leave the matcher positioned on the final occurrence.
        last = pattern.matcher(text);
        last.find(start);
        return last;
    }

    private static String indentOf(String text, int lineStart) {
        int i = lineStart;
        StringBuilder indent = new StringBuilder();
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            indent.append(text.charAt(i));
            i++;
        }
        return indent.toString();
    }

    private static int endOfLine(String text, int from) {
        int idx = text.indexOf('\n', from);
        return idx < 0 ? text.length() : idx;
    }
}
