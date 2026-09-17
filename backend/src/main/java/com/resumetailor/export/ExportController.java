package com.resumetailor.export;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs/{id}")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/export")
    public ResponseEntity<Resource> exportSource(@PathVariable UUID id) {
        return asDownload(exportService.downloadSource(id));
    }

    /** Compiles and returns the PDF in one call, so the UI needs a single round trip. */
    @PostMapping("/pdf")
    public ResponseEntity<Resource> compilePdf(@PathVariable UUID id) {
        return asDownload(exportService.compilePdf(id));
    }

    @GetMapping("/pdf")
    public ResponseEntity<Resource> downloadPdf(@PathVariable UUID id) {
        return asDownload(exportService.downloadPdf(id));
    }

    /** Lets the UI hide the PDF button entirely when compilation is switched off. */
    @GetMapping("/pdf/available")
    public Map<String, Boolean> pdfAvailable(@PathVariable UUID id) {
        return Map.of("enabled", exportService.pdfEnabled());
    }

    private ResponseEntity<Resource> asDownload(DownloadPayload payload) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(payload.filename()).build().toString())
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.content().length)
                .body(new ByteArrayResource(payload.content()));
    }
}
