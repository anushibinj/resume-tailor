package com.resumetailor.pdf;

import com.resumetailor.resume.ResumeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Routes PDF compilation requests according to resume format:
 * <ul>
 *   <li>Markdown (.md) documents are rendered to HTML and converted to PDF entirely in-process
 *       via {@link MarkdownPdfConverter} using flexmark-java and OpenHTMLToPDF. No Docker is invoked.</li>
 *   <li>Other formats (e.g. LaTeX .tex) are dispatched to {@link DockerTexCompiler} to run inside
 *       the sandboxed Docker container.</li>
 * </ul>
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(prefix = "resume-tailor.pdf", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RoutingPdfCompiler implements PdfCompiler {

    private final MarkdownPdfConverter markdownPdfConverter;
    private final DockerTexCompiler dockerTexCompiler;

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public PdfCompileResult compile(String source, ResumeFormat format) {
        if (format == ResumeFormat.MARKDOWN) {
            log.info("Routing Markdown resume to PDF compilation via flexmark-pdf-converter");
            return markdownPdfConverter.convert(source);
        }
        log.info("Routing {} resume to PDF compilation via Docker container", format);
        return dockerTexCompiler.compile(source, format);
    }
}
