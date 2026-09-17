package com.resumetailor.common;

import com.resumetailor.pdf.PdfCompilationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * A document that fails to build is a content problem, not a server fault, so it maps
 * to 422 and carries the compiler log -- that log is the only way the user can fix it.
 */
@Slf4j
// Must run before GlobalExceptionHandler's catch-all; see its javadoc.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PdfExceptionHandler {

    @ExceptionHandler(PdfCompilationException.class)
    public ResponseEntity<ApiError> handleCompileFailure(PdfCompilationException ex) {
        log.info("PDF compilation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Unprocessable Entity", ex.getMessage(),
                        Map.of("compileLog", ex.getCompileLog() == null ? "" : ex.getCompileLog())));
    }
}
