package com.resumetailor.resume;

public enum ResumeFormat {
    LATEX,
    MARKDOWN;

    public String fileExtension() {
        return this == LATEX ? "tex" : "md";
    }
}
