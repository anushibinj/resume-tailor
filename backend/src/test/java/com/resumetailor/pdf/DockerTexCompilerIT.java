package com.resumetailor.pdf;

import com.resumetailor.config.PdfProperties;
import com.resumetailor.resume.ResumeFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compiles real documents in the real sandbox image. Needs Docker and the image built
 * with {@code docker build -t resume-tailor-tex docker/tex}; run with
 * {@code mvn verify -Pintegration}.
 */
@Tag("integration")
class DockerTexCompilerIT {

    private final DockerTexCompiler compiler = new DockerTexCompiler(
            new PdfProperties(true, "docker", "resume-tailor-tex", 120, "1g", "2", "./artifacts"));

    @Test
    void compilesACleanLatexResume() {
        PdfCompileResult result = compiler.compile("""
                \\documentclass{article}
                \\newcommand{\\resumeHeading}[2]{\\noindent\\textbf{#1} \\hfill #2\\par}
                \\begin{document}
                \\section{Experience}
                \\resumeHeading{Senior Engineer, Globex}{2021--Present}
                Improved throughput 50\\% for R\\&D.
                \\end{document}
                """, ResumeFormat.LATEX);

        assertThat(new String(result.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void compilesAMarkdownResume() {
        PdfCompileResult result = compiler.compile("# Jane Doe\n\n## Skills\n- Java\n", ResumeFormat.MARKDOWN);

        assertThat(new String(result.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void refusesShellEscapeFromTheDocument() {
        // LLM-written markup must never be able to run commands during compilation.
        PdfCompileResult result = compiler.compile("""
                \\documentclass{article}
                \\begin{document}
                \\immediate\\write18{touch /work/PWNED}
                Hello.
                \\end{document}
                """, ResumeFormat.LATEX);

        assertThat(result.log()).contains("runsystem(touch /work/PWNED)...disabled.");
    }

    @Test
    void failsLoudlyInsteadOfShippingAPdfWithSilentlyDroppedContent() {
        // nonstopmode would still write a PDF here, with the macro's content missing.
        assertThatThrownBy(() -> compiler.compile("""
                \\documentclass{article}
                \\begin{document}
                Built the billing service \\undefinedmacro{on Kubernetes}.
                \\end{document}
                """, ResumeFormat.LATEX))
                .isInstanceOf(PdfCompilationException.class)
                .hasMessageContaining("Undefined control sequence")
                .satisfies(ex -> assertThat(((PdfCompilationException) ex).getCompileLog()).isNotBlank());
    }
}
