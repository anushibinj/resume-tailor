package com.resumetailor.pdf;

/** Bytes of the compiled PDF plus the compiler log, which is kept for troubleshooting. */
public record PdfCompileResult(byte[] pdfBytes, String log) {
}
