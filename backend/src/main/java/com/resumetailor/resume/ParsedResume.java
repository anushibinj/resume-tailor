package com.resumetailor.resume;

/**
 * A resume split into the part the LLM may rewrite ({@code body}) and the parts it
 * must never see ({@code preamble}, {@code documentTail}).
 *
 * <p>For LaTeX this is the whole point: the preamble carries the document class,
 * packages and custom macros. Keeping it out of the prompt means a misbehaving model
 * can only damage prose, never the thing that makes the document compile.
 *
 * <p>For Markdown {@code preamble} and {@code documentTail} are empty and
 * {@code body} is the entire source.
 */
public record ParsedResume(ResumeFormat format, String preamble, String body, String documentTail) {

    /** Rebuilds a full document from a (possibly rewritten) body. */
    public String reassemble(String newBody) {
        return ResumeParser.reassemble(preamble, newBody, documentTail);
    }
}
