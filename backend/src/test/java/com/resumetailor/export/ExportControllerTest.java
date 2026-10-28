package com.resumetailor.export;

import com.resumetailor.config.CorsProperties;
import com.resumetailor.export.ExportDtos.StoredPdf;
import com.resumetailor.export.ExportDtos.StoredPdfs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code addFilters = false}: this is about request mapping, not auth -- see {@code ResumeControllerTest}. */
@WebMvcTest(ExportController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class ExportControllerTest {

    private static final UUID RUN_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @Test
    void uploadPassesTheRawBodyLengthAndFilenameThrough() throws Exception {
        byte[] pdf = "%PDF-1.7 body".getBytes();
        given(exportService.uploadPdf(eq(RUN_ID), any(), anyLong(), any())).willReturn(
                new StoredPdfs(null, new StoredPdf(pdf.length, "mine.pdf", Instant.parse("2026-10-28T10:00:00Z"))));

        mockMvc.perform(put("/api/runs/{id}/pdfs/uploaded", RUN_ID)
                        .param("filename", "mine.pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(pdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded.originalFilename").value("mine.pdf"))
                .andExpect(jsonPath("$.compiled").isEmpty());

        ArgumentCaptor<InputStream> body = ArgumentCaptor.forClass(InputStream.class);
        verify(exportService).uploadPdf(eq(RUN_ID), body.capture(), eq((long) pdf.length), eq("mine.pdf"));
    }

    @Test
    void listsTheStoredPdfs() throws Exception {
        given(exportService.storedPdfs(RUN_ID)).willReturn(
                new StoredPdfs(new StoredPdf(10, null, Instant.parse("2026-10-28T10:00:00Z")), null));

        mockMvc.perform(get("/api/runs/{id}/pdfs", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compiled.sizeBytes").value(10))
                .andExpect(jsonPath("$.uploaded").isEmpty());
    }

    @Test
    void downloadsTheUploadedPdfAsAnAttachment() throws Exception {
        given(exportService.downloadUploadedPdf(RUN_ID)).willReturn(
                new DownloadPayload("resume-acme.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1, 2}));

        byte[] content = mockMvc.perform(get("/api/runs/{id}/pdfs/uploaded", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resume-acme.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(content).containsExactly(1, 2);
    }

    @Test
    void deletesTheUploadedPdf() throws Exception {
        given(exportService.deleteUploadedPdf(RUN_ID)).willReturn(new StoredPdfs(null, null));

        mockMvc.perform(delete("/api/runs/{id}/pdfs/uploaded", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").isEmpty());
    }
}
