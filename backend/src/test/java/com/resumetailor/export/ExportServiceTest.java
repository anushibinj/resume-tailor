package com.resumetailor.export;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.config.StorageProperties;
import com.resumetailor.export.ExportDtos.StoredPdfs;
import com.resumetailor.pdf.PdfCompileResult;
import com.resumetailor.pdf.PdfCompiler;
import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.storage.FileStorage;
import com.resumetailor.storage.StorageKeys;
import com.resumetailor.tailoring.RunStatus;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringService;
import com.resumetailor.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();

    @Mock
    private TailoringService tailoringService;

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private ArtifactStore artifactStore;

    @Mock
    private PdfCompiler pdfCompiler;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private CurrentUserProvider currentUser;

    private ExportService exportService;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties(
                StorageProperties.StorageType.LOCAL, "./artifacts", DataSize.ofKilobytes(1), null);
        exportService = new ExportService(tailoringService, artifactRepository, artifactStore, pdfCompiler,
                fileStorage, storageProperties, currentUser);
        when(currentUser.currentUserId()).thenReturn(OWNER_ID);
        when(artifactStore.replace(any(), any(), any(), anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    void compilePdfPreservesOutputFilenameAndPassesFormatToCompiler() {
        UUID runId = UUID.randomUUID();
        RunDetail run = completedRun(runId, ResumeFormat.MARKDOWN, "Acme Corp", "Backend Engineer", "# Tailored Resume");

        byte[] pdfBytes = "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8);
        when(tailoringService.getRun(runId)).thenReturn(run);
        when(pdfCompiler.compile(run.tailoredSource(), ResumeFormat.MARKDOWN))
                .thenReturn(new PdfCompileResult(pdfBytes, "compile log"));

        DownloadPayload payload = exportService.compilePdf(runId);

        assertThat(payload.filename()).isEqualTo("resume-acme-corp-backend-engineer.pdf");
        assertThat(payload.contentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(payload.content()).isEqualTo(pdfBytes);

        String expectedKey = StorageKeys.compiledPdf(OWNER_ID, runId);
        verify(fileStorage).put(expectedKey, pdfBytes, MediaType.APPLICATION_PDF_VALUE);
        verify(artifactStore).replace(
                eq(runId), eq(ArtifactStore.KIND_PDF), eq(expectedKey), eq((long) pdfBytes.length),
                eq("compile log"), isNull());
        verify(pdfCompiler).compile(run.tailoredSource(), ResumeFormat.MARKDOWN);
    }

    @Test
    void compilePdfForLatexPassesLatexFormatToCompiler() {
        UUID runId = UUID.randomUUID();
        RunDetail run = completedRun(runId, ResumeFormat.LATEX, "Globex", "Staff Engineer", "\\documentclass{article}");

        byte[] pdfBytes = "%PDF-1.4 latex".getBytes(StandardCharsets.UTF_8);
        when(tailoringService.getRun(runId)).thenReturn(run);
        when(pdfCompiler.compile(run.tailoredSource(), ResumeFormat.LATEX))
                .thenReturn(new PdfCompileResult(pdfBytes, "tex log"));

        DownloadPayload payload = exportService.compilePdf(runId);

        assertThat(payload.filename()).isEqualTo("resume-globex-staff-engineer.pdf");
        assertThat(payload.content()).isEqualTo(pdfBytes);
        verify(pdfCompiler).compile(run.tailoredSource(), ResumeFormat.LATEX);
    }

    @Test
    void recompilingRemovesAFileLeftAtAPreStorageAbsolutePath() {
        UUID runId = UUID.randomUUID();
        RunDetail run = completedRun(runId, ResumeFormat.MARKDOWN, "Acme", "Dev", "# R");
        when(tailoringService.getRun(runId)).thenReturn(run);
        when(pdfCompiler.compile(any(), any())).thenReturn(new PdfCompileResult("%PDF-1".getBytes(), "log"));
        String newKey = StorageKeys.compiledPdf(OWNER_ID, runId);
        when(artifactStore.replace(any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(List.of("/old/artifacts/" + runId + ".pdf", newKey));

        exportService.compilePdf(runId);

        verify(artifactStore).deleteFiles(runId, List.of("/old/artifacts/" + runId + ".pdf"));
    }

    @Test
    void uploadStoresThePdfUnderTheOwnersKeyWithItsCleanedName() {
        UUID runId = UUID.randomUUID();
        when(tailoringService.getRun(runId)).thenReturn(completedRun(runId, ResumeFormat.MARKDOWN, "A", "B", "# R"));
        byte[] pdf = "%PDF-1.7 from pandoc".getBytes(StandardCharsets.UTF_8);

        exportService.uploadPdf(runId, new ByteArrayInputStream(pdf), pdf.length, "C:\\Users\\me\\final\u0007.pdf");

        String key = StorageKeys.uploadedPdf(OWNER_ID, runId);
        verify(fileStorage).put(key, pdf, MediaType.APPLICATION_PDF_VALUE);
        verify(artifactStore).replace(
                eq(runId), eq(ArtifactStore.KIND_UPLOADED_PDF), eq(key), eq((long) pdf.length),
                isNull(), eq("final.pdf"));
    }

    @Test
    void uploadRefusesAFileThatIsNotAPdf() {
        UUID runId = UUID.randomUUID();
        when(tailoringService.getRun(runId)).thenReturn(completedRun(runId, ResumeFormat.MARKDOWN, "A", "B", "# R"));
        byte[] notPdf = "<html>nope</html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> exportService.uploadPdf(runId, new ByteArrayInputStream(notPdf), notPdf.length, "x.pdf"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a PDF");
        verify(fileStorage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void uploadRefusesABodyPastTheLimitEvenWithoutADeclaredLength() {
        UUID runId = UUID.randomUUID();
        when(tailoringService.getRun(runId)).thenReturn(completedRun(runId, ResumeFormat.MARKDOWN, "A", "B", "# R"));
        byte[] big = new byte[2048];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);

        assertThatThrownBy(() -> exportService.uploadPdf(runId, new ByteArrayInputStream(big), -1, "big.pdf"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1 KB upload limit");
        verify(fileStorage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void uploadRefusesADeclaredLengthPastTheLimitBeforeReading() {
        UUID runId = UUID.randomUUID();
        when(tailoringService.getRun(runId)).thenReturn(completedRun(runId, ResumeFormat.MARKDOWN, "A", "B", "# R"));

        assertThatThrownBy(() -> exportService.uploadPdf(runId, new ByteArrayInputStream(new byte[0]), 5000, "x.pdf"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadRefusesARunThatHasNotFinished() {
        UUID runId = UUID.randomUUID();
        RunDetail pending = new RunDetail(
                runId, RunStatus.RUNNING, null, null, ResumeFormat.MARKDOWN, "A", "B",
                UUID.randomUUID(), "Resume", UUID.randomUUID(), "Job", "Orig", null,
                null, null, null, null, null, List.of(), List.of(), List.of(), null,
                Instant.now(), Instant.now(), null);
        when(tailoringService.getRun(runId)).thenReturn(pending);

        assertThatThrownBy(() -> exportService.uploadPdf(runId, new ByteArrayInputStream(new byte[0]), 0, "x.pdf"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void downloadingAnUploadedPdfReadsItFromStorage() {
        UUID runId = UUID.randomUUID();
        when(tailoringService.getRun(runId)).thenReturn(completedRun(runId, ResumeFormat.MARKDOWN, "Acme", "Dev", "# R"));
        Artifact artifact = new Artifact();
        artifact.setStorageKey("stored-key");
        when(artifactRepository.findFirstByRunIdAndKindOrderByCreatedAtDesc(runId, ArtifactStore.KIND_UPLOADED_PDF))
                .thenReturn(Optional.of(artifact));
        when(fileStorage.get("stored-key")).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        DownloadPayload payload = exportService.downloadUploadedPdf(runId);

        assertThat(payload.filename()).isEqualTo("resume-acme-dev.pdf");
        assertThat(payload.content()).containsExactly(1, 2, 3);
    }

    @Test
    void deletingTheUploadRemovesItsRowAndFileButChecksOwnershipFirst() {
        UUID runId = UUID.randomUUID();
        when(artifactStore.removeRows(runId, ArtifactStore.KIND_UPLOADED_PDF)).thenReturn(List.of("k"));

        StoredPdfs left = exportService.deleteUploadedPdf(runId);

        verify(tailoringService, atLeastOnce()).requireOwned(runId);
        verify(artifactStore).deleteFiles(runId, List.of("k"));
        assertThat(left.uploaded()).isNull();
    }

    @Test
    void cleanFilenameDropsDirectoriesAndControlCharacters() {
        assertThat(ExportService.cleanFilename("/tmp/../a/resume.pdf")).isEqualTo("resume.pdf");
        assertThat(ExportService.cleanFilename("  ")).isNull();
        assertThat(ExportService.cleanFilename(null)).isNull();
        assertThat(ExportService.cleanFilename("x".repeat(400))).hasSize(255);
    }

    private static RunDetail completedRun(UUID runId, ResumeFormat format, String company, String role, String source) {
        return new RunDetail(
                runId, RunStatus.COMPLETED, null, null, format, company, role,
                UUID.randomUUID(), "Resume", UUID.randomUUID(), "Job", "Orig source", source,
                "gpt-4o", 100, 100, 85, null, List.of(), List.of(), List.of(), null,
                Instant.now(), Instant.now(), Instant.now());
    }
}
