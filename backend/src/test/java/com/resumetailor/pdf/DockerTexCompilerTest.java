package com.resumetailor.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Engine selection only -- anything needing a real container is tagged "integration". */
class DockerTexCompilerTest {

    @Test
    void usesPdflatexForAStandardTemplate() {
        String source = "\\documentclass{article}\n\\usepackage{geometry}\n\\begin{document}x\\end{document}";

        assertThat(DockerTexCompiler.latexEngineFor(source)).isEqualTo("pdflatex");
    }

    @Test
    void findsTheFirstTexErrorInALog() {
        String log = """
                (./main.tex
                LaTeX Warning: Reference `x' undefined.
                ! Undefined control sequence.
                l.5 \\undefinedmacro
                ! LaTeX Error: \\begin{itemize} ended by \\end{document}.
                """;

        assertThat(DockerTexCompiler.firstTexError(log)).isEqualTo("! Undefined control sequence.");
    }

    @Test
    void warningsAloneAreNotErrors() {
        String log = """
                Overfull \\hbox (12.3pt too wide) in paragraph at lines 10--12
                LaTeX Warning: Label(s) may have changed. Rerun to get cross-references right.
                runsystem(touch /work/PWNED)...disabled.
                Output written on main.pdf (1 page, 54195 bytes).
                """;

        assertThat(DockerTexCompiler.firstTexError(log)).isNull();
    }

    @Test
    void switchesToXelatexForTemplatesThatNeedSystemFonts() {
        assertThat(DockerTexCompiler.latexEngineFor("\\usepackage{fontspec}")).isEqualTo("xelatex");
        assertThat(DockerTexCompiler.latexEngineFor("\\setmainfont{Calibri}")).isEqualTo("xelatex");
        assertThat(DockerTexCompiler.latexEngineFor("\\usepackage{unicode-math}")).isEqualTo("xelatex");
        assertThat(DockerTexCompiler.latexEngineFor("\\usepackage{polyglossia}")).isEqualTo("xelatex");
    }
}
