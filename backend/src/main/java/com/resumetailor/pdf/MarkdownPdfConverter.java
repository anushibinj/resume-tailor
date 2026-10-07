package com.resumetailor.pdf;

import com.vladsch.flexmark.pdf.converter.PdfConverterExtension;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Converts Markdown resumes to PDF entirely in-process using flexmark-java,
 * custom CSS styling, and flexmark-pdf-converter / OpenHTMLToPDF.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownPdfConverter {

    private static final String CSS_RESOURCE_PATH = "/pdf/markdown.css";
    private static final String PDF_HEADER = "%PDF-";

    private final MarkdownRenderer markdownRenderer;

    private String defaultCss = "";

    @PostConstruct
    void init() {
        this.defaultCss = loadCssResource(CSS_RESOURCE_PATH);
    }

    /**
     * Converts a Markdown resume string into a PDF compile result containing
     * the compiled bytes and a summary log.
     */
    public PdfCompileResult convert(String markdown) {
        String html = renderCompleteHtml(markdown);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfConverterExtension.exportToPdf(out, html, "", markdownRenderer.getOptions());
            byte[] pdfBytes = out.toByteArray();

            if (pdfBytes.length < 5 || !new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII).startsWith(PDF_HEADER)) {
                throw new PdfCompilationException(
                        "The document did not compile into a valid PDF.",
                        "Flexmark PDF converter output was invalid or empty.");
            }

            String log = "Rendered Markdown to PDF successfully via flexmark-pdf-converter (" + pdfBytes.length + " bytes)\n";
            return new PdfCompileResult(pdfBytes, log);
        } catch (PdfCompilationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Markdown PDF compilation failed: {}", ex.getMessage(), ex);
            throw new PdfCompilationException(
                    "Failed to compile Markdown to PDF: " + ex.getMessage(),
                    ex.getMessage() != null ? ex.getMessage() : "");
        }
    }

    /**
     * Renders Markdown content into a complete HTML document with embedded CSS.
     */
    public String renderCompleteHtml(String markdown) {
        return renderCompleteHtml(markdown, defaultCss);
    }

    /**
     * Renders Markdown content into a complete HTML document with specific CSS embedded.
     */
    public String renderCompleteHtml(String markdown, String css) {
        String bodyHtml = markdownRenderer.renderToHtml(markdown);
        return assembleHtmlDocument(bodyHtml, css);
    }

    private String assembleHtmlDocument(String bodyHtml, String css) {
        return "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <style>\n"
                + (css != null ? css : "") + "\n"
                + "    </style>\n"
                + "</head>\n"
                + "<body>\n"
                + (bodyHtml != null ? bodyHtml : "") + "\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String loadCssResource(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Markdown PDF CSS resource '{}' not found on classpath", path);
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Could not load Markdown PDF CSS resource '{}': {}", path, ex.getMessage());
            return "";
        }
    }

    public String getDefaultCss() {
        return defaultCss;
    }
}
