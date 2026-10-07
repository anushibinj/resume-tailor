package com.resumetailor.export;

import com.resumetailor.config.PdfProperties;
import com.resumetailor.pdf.PdfCompileResult;
import com.resumetailor.pdf.PdfCompiler;
import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.tailoring.RunStatus;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private TailoringService tailoringService;

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private ArtifactStore artifactStore;

    @Mock
    private PdfCompiler pdfCompiler;

    @TempDir
    private Path tempDir;

    private ExportService exportService;
    private PdfProperties pdfProperties;

    @BeforeEach
    void setUp() {
        pdfProperties = new PdfProperties(
                true, "docker", "resume-tailor-tex", 120, "1g", "2", tempDir.toString());
        exportService = new ExportService(
                tailoringService, artifactRepository, artifactStore, pdfCompiler, pdfProperties);
    }

    @Test
    void compilePdfPreservesOutputFilenameAndPassesFormatToCompiler() {
        UUID runId = UUID.randomUUID();
        RunDetail run = new RunDetail(
                runId, RunStatus.COMPLETED, null, null, ResumeFormat.MARKDOWN, "Acme Corp", "Backend Engineer",
                UUID.randomUUID(), "Resume", UUID.randomUUID(), "Job", "Orig source", "# Tailored Resume",
                "gpt-4o", 100, 100, 85, null, List.of(), List.of(), List.of(), null,
                Instant.now(), Instant.now(), Instant.now());

        byte[] pdfBytes = "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8);
        when(tailoringService.getRun(runId)).thenReturn(run);
        when(pdfCompiler.compile(run.tailoredSource(), ResumeFormat.MARKDOWN))
                .thenReturn(new PdfCompileResult(pdfBytes, "compile log"));

        DownloadPayload payload = exportService.compilePdf(runId);

        assertThat(payload.filename()).isEqualTo("resume-acme-corp-backend-engineer.pdf");
        assertThat(payload.contentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(payload.content()).isEqualTo(pdfBytes);

        Path expectedFile = tempDir.resolve(runId + ".pdf");
        assertThat(Files.exists(expectedFile)).isTrue();

        verify(artifactStore).replacePdf(
                eq(runId), eq(expectedFile.toAbsolutePath().toString()), eq((long) pdfBytes.length), eq("compile log"));
        verify(pdfCompiler).compile(run.tailoredSource(), ResumeFormat.MARKDOWN);
    }

    @Test
    void compilePdfForLatexPassesLatexFormatToCompiler() {
        UUID runId = UUID.randomUUID();
        RunDetail run = new RunDetail(
                runId, RunStatus.COMPLETED, null, null, ResumeFormat.LATEX, "Globex", "Staff Engineer",
                UUID.randomUUID(), "Resume", UUID.randomUUID(), "Job", "Orig source", "\\documentclass{article}",
                "gpt-4o", 100, 100, 85, null, List.of(), List.of(), List.of(), null,
                Instant.now(), Instant.now(), Instant.now());

        byte[] pdfBytes = "%PDF-1.4 latex".getBytes(StandardCharsets.UTF_8);
        when(tailoringService.getRun(runId)).thenReturn(run);
        when(pdfCompiler.compile(run.tailoredSource(), ResumeFormat.LATEX))
                .thenReturn(new PdfCompileResult(pdfBytes, "tex log"));

        DownloadPayload payload = exportService.compilePdf(runId);

        assertThat(payload.filename()).isEqualTo("resume-globex-staff-engineer.pdf");
        assertThat(payload.content()).isEqualTo(pdfBytes);
        verify(pdfCompiler).compile(run.tailoredSource(), ResumeFormat.LATEX);
    }
}
