package com.resumetailor.tailoring;

import com.resumetailor.resume.LatexComments;
import com.resumetailor.resume.ResumeFormat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies an accepted addition to a resume body.
 *
 * <p>Additions are anchored to text already in the resume, so they land inside whatever
 * structure the template uses -- a comma list in an AltaCV {@code \cvachievement}, an
 * {@code itemize}, a Markdown bullet -- rather than being appended somewhere generic.
 *
 * <p>Two guarantees hold whatever the model returns:
 * <ul>
 *   <li><b>Insert only.</b> Text is inserted beside the anchor; nothing in the resume is
 *       removed. Removing an addition is therefore exact -- the document is rebuilt from
 *       the model's pristine output plus whatever is still accepted.</li>
 *   <li><b>Never into a comment.</b> An anchor inside a commented-out LaTeX line is
 *       skipped: the text would be invisible in the PDF while the UI claimed it was added.</li>
 * </ul>
 */
public final class AdditionApplier {

    private AdditionApplier() {
    }

    public static String apply(String body, ResumeFormat format, RunSuggestion suggestion) {
        String insert = suggestion.getInsertText();
        String anchor = suggestion.getAnchor();

        if (anchor != null && !anchor.isBlank() && insert != null && !insert.isBlank()) {
            String applied = insertAtAnchor(body, format, anchor, insert, suggestion.getPlacement());
            if (applied != null) {
                return applied;
            }
        }
        // No usable anchor: fall back to placing it by section.
        String text = insert != null && !insert.isBlank() ? insert.strip() : suggestion.getContent();
        return SuggestionSplicer.splice(
                body, format, suggestion.getKind(), suggestion.getTargetSection(), text);
    }

    /** Returns the edited body, or null when the anchor cannot be used. */
    private static String insertAtAnchor(String body, ResumeFormat format, String anchor,
                                         String insert, Placement placement) {
        int[] span = findAnchor(body, format, anchor);
        if (span == null) {
            return null;
        }
        String text = format == ResumeFormat.LATEX ? SuggestionSplicer.escapeLatex(insert) : insert;

        // A model sometimes returns the anchor plus the new text as one string. Splicing that
        // in verbatim would duplicate the anchor, so treat it as a replacement instead --
        // still additive, because the anchor text survives inside the replacement.
        if (text.contains(anchor.strip())) {
            return body.substring(0, span[0]) + text + body.substring(span[1]);
        }
        int at = placement == Placement.BEFORE ? span[0] : span[1];
        return body.substring(0, at) + text + body.substring(at);
    }

    /**
     * Locates the anchor, preferring an exact match and falling back to one where runs of
     * whitespace differ -- models routinely re-wrap the lines they quote back.
     */
    static int[] findAnchor(String body, ResumeFormat format, String anchor) {
        String needle = anchor.strip();
        if (needle.isEmpty()) {
            return null;
        }

        int from = 0;
        while (true) {
            int index = body.indexOf(needle, from);
            if (index < 0) {
                break;
            }
            if (!isCommented(body, format, index)) {
                return new int[]{index, index + needle.length()};
            }
            from = index + 1;
        }

        Matcher matcher = whitespaceTolerant(needle).matcher(body);
        while (matcher.find()) {
            if (!isCommented(body, format, matcher.start())) {
                return new int[]{matcher.start(), matcher.end()};
            }
        }
        return null;
    }

    private static Pattern whitespaceTolerant(String needle) {
        String[] parts = needle.split("\\s+");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString());
    }

    private static boolean isCommented(String text, ResumeFormat format, int position) {
        // Markdown has no line comments, so this only applies to LaTeX.
        return format == ResumeFormat.LATEX && LatexComments.isCommented(text, position);
    }
}
