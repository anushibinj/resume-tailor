package com.resumetailor.pdf;

import com.resumetailor.resume.ResumeFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingPdfCompilerTest {

    @Mock
    private MarkdownPdfConverter markdownPdfConverter;

    @Mock
    private DockerTexCompiler dockerTexCompiler;

    private RoutingPdfCompiler routingPdfCompiler;

    @BeforeEach
    void setUp() {
        routingPdfCompiler = new RoutingPdfCompiler(markdownPdfConverter, dockerTexCompiler);
    }

    @Test
    void routesMarkdownToFlexmarkWithoutInvokingDocker() {
        String mdSource = "# John Doe\n## Experience\nEngineer";
        byte[] expectedPdf = "%PDF-1.4 simulated pdf".getBytes(StandardCharsets.UTF_8);
        PdfCompileResult expectedResult = new PdfCompileResult(expectedPdf, "flexmark log");

        when(markdownPdfConverter.convert(mdSource)).thenReturn(expectedResult);

        PdfCompileResult actualResult = routingPdfCompiler.compile(mdSource, ResumeFormat.MARKDOWN);

        assertThat(actualResult).isEqualTo(expectedResult);
        verify(markdownPdfConverter).convert(mdSource);
        verifyNoInteractions(dockerTexCompiler);
    }

    @Test
    void routesLatexToDockerWithoutInvokingMarkdownConverter() {
        String latexSource = "\\documentclass{article}\\begin{document}Hello\\end{document}";
        byte[] expectedPdf = "%PDF-1.4 simulated latex pdf".getBytes(StandardCharsets.UTF_8);
        PdfCompileResult expectedResult = new PdfCompileResult(expectedPdf, "latex log");

        when(dockerTexCompiler.compile(latexSource, ResumeFormat.LATEX)).thenReturn(expectedResult);

        PdfCompileResult actualResult = routingPdfCompiler.compile(latexSource, ResumeFormat.LATEX);

        assertThat(actualResult).isEqualTo(expectedResult);
        verify(dockerTexCompiler).compile(latexSource, ResumeFormat.LATEX);
        verifyNoInteractions(markdownPdfConverter);
    }

    @Test
    void compilerIsEnabledWhenRegistered() {
        assertThat(routingPdfCompiler.enabled()).isTrue();
    }
}
