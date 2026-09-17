package com.resumetailor.pdf;

import lombok.Getter;

/**
 * The document itself failed to build. Distinct from Docker being unavailable: this is
 * a problem with the markup, and the log is what the user needs to fix it.
 */
@Getter
public class PdfCompilationException extends RuntimeException {

    private final String compileLog;

    public PdfCompilationException(String message, String compileLog) {
        super(message);
        this.compileLog = compileLog;
    }
}
