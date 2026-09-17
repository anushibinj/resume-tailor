package com.resumetailor.resume;

/**
 * Whether a position in LaTeX source sits inside a comment.
 *
 * <p>Resume templates keep large blocks commented out -- alternative sections, unused
 * examples. Anything found there is invisible in the PDF, so it must not be treated as a
 * real section, and an addition must never be placed into one.
 */
public final class LatexComments {

    private LatexComments() {
    }

    /** True when an unescaped {@code %} appears earlier on the same line. */
    public static boolean isCommented(String text, int position) {
        int lineStart = text.lastIndexOf('\n', Math.max(position - 1, 0)) + 1;
        for (int i = lineStart; i < position && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '%') {
                return true;
            }
        }
        return false;
    }
}
