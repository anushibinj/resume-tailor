package com.resumetailor.llm;

/**
 * Raised when the user's LLM endpoint is unreachable, rejects the request, or returns
 * something unusable. The message is shown to the user, so it should say what to fix.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
