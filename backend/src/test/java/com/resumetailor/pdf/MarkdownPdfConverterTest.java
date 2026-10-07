package com.resumetailor.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownPdfConverterTest {

    private MarkdownPdfConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MarkdownPdfConverter(new MarkdownRenderer());
        converter.init();
    }

    @Test
    void includesCustomCssInCompleteHtml() {
        String md = "# Test Resume\n\nSome text.";
        String html = converter.renderCompleteHtml(md);

        assertThat(html)
                .contains("<!DOCTYPE html>")
                .contains("<head>")
                .contains("<meta charset=\"UTF-8\">")
                .contains("<style>")
                .contains("@page {")
                .contains("size: A4;")
                .contains("</style>")
                .contains("<body>")
                .contains("<h1>Test Resume</h1>")
                .contains("<p>Some text.</p>")
                .contains("</body>")
                .contains("</html>");
    }

    @Test
    void generatesValidPdfForMarkdown() {
        String md = """
                # Jane Doe
                
                **Senior Software Engineer** | jane@example.com
                
                ## Summary
                Experienced backend engineer with 8+ years building scalable systems in Java.
                
                ## Experience
                ### Tech Corp — Senior Engineer (2020 - Present)
                - Designed and built microservices handling 10k req/s.
                - Reduced latency by 40% using Redis caching and reactive pipelines.
                
                ## Skills
                | Category | Technologies |
                | :--- | :--- |
                | Languages | Java, Python, SQL |
                | Frameworks | Spring Boot, Hibernate |
                """;

        PdfCompileResult result = converter.convert(md);

        assertThat(result).isNotNull();
        assertThat(result.pdfBytes()).isNotEmpty();
        assertThat(new String(result.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(result.log()).contains("flexmark-pdf-converter");
    }

    @Test
    void generatesValidPdfForEmptyMarkdown() {
        PdfCompileResult result = converter.convert("");

        assertThat(result).isNotNull();
        assertThat(result.pdfBytes()).isNotEmpty();
        assertThat(new String(result.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void allowsOverridingCssInRenderCompleteHtml() {
        String customCss = "body { color: red; }";
        String html = converter.renderCompleteHtml("# Hello", customCss);

        assertThat(html).contains("body { color: red; }");
        assertThat(html).contains("<h1>Hello</h1>");
    }
}
