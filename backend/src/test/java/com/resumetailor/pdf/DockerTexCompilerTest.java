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
    void switchesToXelatexForTemplatesThatNeedSystemFonts() {
        assertThat(DockerTexCompiler.latexEngineFor("\\usepackage{fontspec}")).isEqualTo("xelatex");
        assertThat(DockerTexCompiler.latexEngineFor("\\setmainfont{Calibri}")).isEqualTo("xelatex");
        assertThat(DockerTexCompiler.latexEngineFor("\\usepackage{unicode-math}")).isEqualTo("xelatex");
        assertThat(DockerTexCompiler.latexEngineFor("\\usepackage{polyglossia}")).isEqualTo("xelatex");
    }
}
