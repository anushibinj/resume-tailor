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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String KIND_PDF = "PDF";

    private final TailoringService tailoringService;
    private final ArtifactRepository artifactRepository;
    private final PdfCompiler pdfCompiler;
    private final PdfProperties pdfProperties;

    @Transactional(readOnly = true)
    public DownloadPayload downloadSource(UUID runId) {
        RunDetail run = requireCompleted(runId);
        String filename = Filenames.forRun(run.company(), run.role(), run.format().fileExtension());
        return new DownloadPayload(
                filename,
                MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
                run.tailoredSource().getBytes(StandardCharsets.UTF_8));
    }

    /** Compiles and caches a PDF for the run's current content (accepted suggestions included). */
    @Transactional
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

        // One stored PDF per run: a recompile after accepting a suggestion replaces it.
        artifactRepository.deleteAllByRunId(runId);
        Artifact artifact = new Artifact();
        artifact.setRunId(runId);
        artifact.setKind(KIND_PDF);
        artifact.setFilePath(target.toAbsolutePath().toString());
        artifact.setContentType(MediaType.APPLICATION_PDF_VALUE);
        artifact.setSizeBytes(result.pdfBytes().length);
        artifact.setCompileLog(result.log());
        artifactRepository.save(artifact);

        return new DownloadPayload(
                Filenames.forRun(run.company(), run.role(), "pdf"),
                MediaType.APPLICATION_PDF_VALUE,
                result.pdfBytes());
    }

    @Transactional(readOnly = true)
    public DownloadPayload downloadPdf(UUID runId) {
        RunDetail run = requireCompleted(runId);
        Artifact artifact = artifactRepository.findFirstByRunIdAndKindOrderByCreatedAtDesc(runId, KIND_PDF)
                .orElseThrow(() -> new BadRequestException(
                        "No PDF has been compiled for this run yet. Compile it first."));
        try {
            byte[] content = Files.readAllBytes(Path.of(artifact.getFilePath()));
            return new DownloadPayload(
                    Filenames.forRun(run.company(), run.role(), "pdf"),
                    MediaType.APPLICATION_PDF_VALUE,
                    content);
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
