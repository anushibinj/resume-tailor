package com.resumetailor.common;

import com.resumetailor.llm.LlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The user's LLM endpoint is an upstream dependency, so its failures map to 502 and
 * carry the provider's own message -- that text is what tells the user what to fix.
 */
@Slf4j
// Must run before GlobalExceptionHandler's catch-all; see its javadoc.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class LlmExceptionHandler {

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiError> handleLlm(LlmException ex) {
        log.warn("LLM call failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(502, "Bad Gateway", ex.getMessage()));
    }
}
