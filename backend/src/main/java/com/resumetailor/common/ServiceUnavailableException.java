package com.resumetailor.common;

/**
 * Maps to HTTP 503. Used when an optional dependency the user controls is not
 * reachable -- most often Docker being stopped when a PDF is requested -- so the
 * UI can show a fix-it message rather than a generic failure.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
