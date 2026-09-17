package com.resumetailor.export;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.ServiceUnavailableException;
import com.resumetailor.config.PdfProperties;
import com.resumetailor.pdf.PdfCompileResult;
import com.resumetailor.pdf.PdfCompiler;
import com.resumetailor.tailoring.RunStatus;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Turns a finished run into a downloadable file.
 *
 * <p>Deliberately not {@code @Transactional}: compiling a PDF can take a minute, and the
 * reads and writes it needs each manage their own transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TailoringService tailoringService;
    private final ArtifactRepository artifactRepository;
    private final ArtifactStore artifactStore;
    private final PdfCompiler pdfCompiler;
    private final PdfProperties pdfProperties;

    public DownloadPayload downloadSource(UUID runId) {
        RunDetail run = requireCompleted(runId);
        return new DownloadPayload(
                Filenames.forRun(run.company(), run.role(), run.format().fileExtension()),
                MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
                run.tailoredSource().getBytes(StandardCharsets.UTF_8));
    }

    /** Compiles the run's current content -- accepted suggestions included -- and caches it. */
    public DownloadPayload compilePdf(UUID runId) {
        RunDetail run = requireCompleted(runId);
        PdfCompileResult result = pdfCompiler.compile(run.tailoredSource(), run.format());

        Path target = artifactPath(runId);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, result.pdfBytes());
        } catch (IOException ex) {
            throw new ServiceUnavailableException("Could not save the compiled PDF: " + ex.getMessage(), ex);
        }
        artifactStore.replacePdf(
                runId, target.toAbsolutePath().toString(), result.pdfBytes().length, result.log());

        return new DownloadPayload(
                Filenames.forRun(run.company(), run.role(), "pdf"),
                MediaType.APPLICATION_PDF_VALUE,
                result.pdfBytes());
    }

    public DownloadPayload downloadPdf(UUID runId) {
        RunDetail run = requireCompleted(runId);
        Artifact artifact = artifactRepository
                .findFirstByRunIdAndKindOrderByCreatedAtDesc(runId, ArtifactStore.KIND_PDF)
                .orElseThrow(() -> new BadRequestException(
                        "No PDF has been compiled for this run yet. Compile it first."));
        try {
            return new DownloadPayload(
                    Filenames.forRun(run.company(), run.role(), "pdf"),
                    MediaType.APPLICATION_PDF_VALUE,
                    Files.readAllBytes(Path.of(artifact.getFilePath())));
        } catch (IOException ex) {
            throw new BadRequestException("The compiled PDF is no longer on disk. Compile it again.");
        }
    }

    public boolean pdfEnabled() {
        return pdfCompiler.enabled();
    }

    private Path artifactPath(UUID runId) {
        return Path.of(pdfProperties.artifactPath(), runId + ".pdf");
    }

    private RunDetail requireCompleted(UUID runId) {
        RunDetail run = tailoringService.getRun(runId);
        if (run.status() != RunStatus.COMPLETED || run.tailoredSource() == null) {
            throw new BadRequestException("This run has not produced a tailored resume yet");
        }
        return run;
    }
}
