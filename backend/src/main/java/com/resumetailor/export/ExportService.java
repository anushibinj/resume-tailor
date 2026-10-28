package com.resumetailor.export;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.config.StorageProperties;
import com.resumetailor.export.ExportDtos.StoredPdf;
import com.resumetailor.export.ExportDtos.StoredPdfs;
import com.resumetailor.pdf.PdfCompileResult;
import com.resumetailor.pdf.PdfCompiler;
import com.resumetailor.storage.FileStorage;
import com.resumetailor.storage.StorageKeys;
import com.resumetailor.tailoring.RunStatus;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringService;
import com.resumetailor.user.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Turns a finished run into a downloadable file, and keeps the PDFs that belong to it --
 * compiled here, or made elsewhere and uploaded -- in the configured {@link FileStorage}.
 *
 * <p>Deliberately not {@code @Transactional}: compiling a PDF can take a minute, and the
 * reads and writes it needs each manage their own transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILENAME_LENGTH = 255;

    private final TailoringService tailoringService;
    private final ArtifactRepository artifactRepository;
    private final ArtifactStore artifactStore;
    private final PdfCompiler pdfCompiler;
    private final FileStorage fileStorage;
    private final StorageProperties storageProperties;
    private final CurrentUserProvider currentUser;

    public DownloadPayload downloadSource(UUID runId) {
        RunDetail run = requireCompleted(runId);
        return new DownloadPayload(
                Filenames.forRun(run.company(), run.role(), run.format().fileExtension()),
                MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
                run.tailoredSource().getBytes(StandardCharsets.UTF_8));
    }

    /** Compiles the run's current content -- accepted suggestions included -- and keeps it. */
    public DownloadPayload compilePdf(UUID runId) {
        RunDetail run = requireCompleted(runId);
        PdfCompileResult result = pdfCompiler.compile(run.tailoredSource(), run.format());

        store(runId, ArtifactStore.KIND_PDF, StorageKeys.compiledPdf(currentUser.currentUserId(), runId),
                result.pdfBytes(), result.log(), null);

        return new DownloadPayload(
                Filenames.forRun(run.company(), run.role(), "pdf"),
                MediaType.APPLICATION_PDF_VALUE,
                result.pdfBytes());
    }

    public DownloadPayload downloadPdf(UUID runId) {
        return download(requireCompleted(runId), ArtifactStore.KIND_PDF,
                "No PDF has been compiled for this run yet. Compile it first.",
                "The compiled PDF is no longer in storage. Compile it again.");
    }

    /**
     * Keeps a PDF the user made outside the app -- say, the exported Markdown converted with
     * their own tool -- beside the run, replacing any earlier upload.
     *
     * <p>The body is read no further than the configured limit, so a client cannot make the
     * server buffer an arbitrarily large request by lying about (or omitting) its length.
     */
    public StoredPdfs uploadPdf(UUID runId, InputStream body, long declaredLength, String filename) {
        requireCompleted(runId);
        long limit = storageProperties.maxUploadSize().toBytes();
        if (declaredLength > limit) {
            throw tooLarge(limit);
        }
        byte[] content;
        try {
            content = body.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE - 8));
        } catch (IOException ex) {
            throw new BadRequestException("The upload did not finish: " + ex.getMessage());
        }
        if (content.length > limit) {
            throw tooLarge(limit);
        }
        if (content.length == 0) {
            throw new BadRequestException("The uploaded file is empty.");
        }
        if (!isPdf(content)) {
            throw new BadRequestException("That file is not a PDF.");
        }

        store(runId, ArtifactStore.KIND_UPLOADED_PDF,
                StorageKeys.uploadedPdf(currentUser.currentUserId(), runId),
                content, null, cleanFilename(filename));
        return storedPdfs(runId);
    }

    public DownloadPayload downloadUploadedPdf(UUID runId) {
        return download(requireCompleted(runId), ArtifactStore.KIND_UPLOADED_PDF,
                "No PDF has been uploaded for this run.",
                "The uploaded PDF is no longer in storage. Upload it again.");
    }

    public StoredPdfs deleteUploadedPdf(UUID runId) {
        tailoringService.requireOwned(runId);
        List<String> removed = artifactStore.removeRows(runId, ArtifactStore.KIND_UPLOADED_PDF);
        artifactStore.deleteFiles(runId, removed);
        return storedPdfs(runId);
    }

    /** What is kept for the run right now, without reading the files themselves. */
    public StoredPdfs storedPdfs(UUID runId) {
        tailoringService.requireOwned(runId);
        return new StoredPdfs(latest(runId, ArtifactStore.KIND_PDF), latest(runId, ArtifactStore.KIND_UPLOADED_PDF));
    }

    public boolean pdfEnabled() {
        return pdfCompiler.enabled();
    }

    /**
     * File first, row second: a row never points at a file that was not written. A row that
     * replaced one pointing elsewhere (a pre-storage absolute path) takes that file with it.
     */
    private void store(UUID runId, String kind, String key, byte[] content, String log, String originalFilename) {
        fileStorage.put(key, content, MediaType.APPLICATION_PDF_VALUE);
        List<String> replaced = artifactStore.replace(runId, kind, key, content.length, log, originalFilename);
        artifactStore.deleteFiles(runId, replaced.stream().filter(old -> !old.equals(key)).toList());
    }

    private DownloadPayload download(RunDetail run, String kind, String noneMessage, String goneMessage) {
        Artifact artifact = artifactRepository
                .findFirstByRunIdAndKindOrderByCreatedAtDesc(run.id(), kind)
                .orElseThrow(() -> new BadRequestException(noneMessage));
        byte[] content = fileStorage.get(artifact.getStorageKey())
                .orElseThrow(() -> new BadRequestException(goneMessage));
        return new DownloadPayload(
                Filenames.forRun(run.company(), run.role(), "pdf"),
                MediaType.APPLICATION_PDF_VALUE,
                content);
    }

    private StoredPdf latest(UUID runId, String kind) {
        return artifactRepository.findFirstByRunIdAndKindOrderByCreatedAtDesc(runId, kind)
                .map(StoredPdf::of)
                .orElse(null);
    }

    private static boolean isPdf(byte[] content) {
        return content.length >= PDF_MAGIC.length
                && Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC);
    }

    private static BadRequestException tooLarge(long limit) {
        String readable = limit >= 1024 * 1024 ? (limit / (1024 * 1024)) + " MB" : (limit / 1024) + " KB";
        return new BadRequestException(
                "That PDF is larger than the " + readable + " upload limit (UPLOAD_PDF_MAX_SIZE).");
    }

    /** The file's own name, kept for display: no directories, no control characters. */
    static String cleanFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.replaceAll("^.*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
    }

    private RunDetail requireCompleted(UUID runId) {
        RunDetail run = tailoringService.getRun(runId);
        if (run.status() != RunStatus.COMPLETED || run.tailoredSource() == null) {
            throw new BadRequestException("This run has not produced a tailored resume yet");
        }
        return run;
    }
}
