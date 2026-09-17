package com.resumetailor.pdf;

import com.resumetailor.resume.ResumeFormat;

public interface PdfCompiler {

    boolean enabled();

    /** Compiles a complete resume document to PDF, or throws with a message worth showing. */
    PdfCompileResult compile(String source, ResumeFormat format);
}
