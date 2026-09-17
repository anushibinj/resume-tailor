package com.resumetailor.resume;

/**
 * One section of a resume body.
 *
 * @param title       section heading, or {@code null} for the content that appears
 *                    before the first heading (name, contact line, and so on)
 * @param level       1 for top-level sections, 2+ for subsections
 * @param startOffset inclusive offset into the body the section starts at
 * @param endOffset   exclusive offset into the body the section ends at
 * @param content     the section text including its own heading
 */
public record ResumeSection(String title, int level, int startOffset, int endOffset, String content) {

    /** Label suitable for display when {@link #title()} is null. */
    public String displayTitle() {
        return title == null || title.isBlank() ? "Header" : title;
    }
}
