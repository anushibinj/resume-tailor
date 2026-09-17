package com.resumetailor.llm;

/**
 * Pulls a JSON object out of a model response.
 *
 * <p>Needed because {@code response_format: json_object} is an OpenAI extension that
 * many compatible servers ignore: local runtimes in particular like to wrap JSON in
 * ```json fences or prefix it with a sentence. Rather than fail the whole run on that,
 * we recover the outermost balanced object.
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    public static String extractObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmException("The model returned an empty response");
        }
        String text = stripFences(raw.trim());

        int start = text.indexOf('{');
        if (start < 0) {
            throw new LlmException("The model response contained no JSON object. Response began: "
                    + preview(text));
        }
        int end = findMatchingBrace(text, start);
        if (end < 0) {
            throw new LlmException("The model response contained truncated JSON -- the model may have hit "
                    + "its output token limit. Try raising Max output tokens in Settings.");
        }
        return text.substring(start, end + 1);
    }

    private static String stripFences(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String withoutOpening = text.substring(firstNewline + 1);
        int closing = withoutOpening.lastIndexOf("```");
        return closing >= 0 ? withoutOpening.substring(0, closing).trim() : withoutOpening.trim();
    }

    /** Brace matching that ignores braces appearing inside JSON string literals. */
    private static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
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

    private static String preview(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }
}
