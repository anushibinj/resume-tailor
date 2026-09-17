package com.resumetailor.common;

import java.time.Instant;
import java.util.Map;

/**
 * Single error shape for every failed request.
 *
 * @param fieldErrors populated only for bean-validation failures, so the frontend can
 *                    attach messages to individual form fields.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
