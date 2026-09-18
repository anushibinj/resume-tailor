package com.resumetailor.common;

import com.resumetailor.config.CorsProperties;
import com.resumetailor.export.ExportController;
import com.resumetailor.export.ExportService;
import com.resumetailor.llm.LlmException;
import com.resumetailor.pdf.PdfCompilationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each specific exception must reach its own handler rather than the catch-all.
 *
 * <p>Spring does not pick the most specific handler across separate
 * {@code @RestControllerAdvice} beans -- it uses the first bean that matches at all. A
 * catch-all ordered ahead of the specific handlers turns every one of these into a
 * generic 500 and throws away the message the user needs. Found running the real stack.
 *
 * <p>{@code addFilters = false}: this is about exception-to-status mapping, not auth --
 * see the note on {@code ResumeControllerTest}.
 */
@WebMvcTest(ExportController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class ExceptionMappingTest {

    private static final UUID RUN_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @Test
    void aDocumentThatFailsToCompileIs422WithTheTexErrorAndLog() throws Exception {
        willThrow(new PdfCompilationException(
                "The document did not compile cleanly. ! Undefined control sequence.", "l.5 \\undefinedmacro"))
                .given(exportService).compilePdf(any());

        mockMvc.perform(post("/api/runs/{id}/pdf", RUN_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "The document did not compile cleanly. ! Undefined control sequence."))
                .andExpect(jsonPath("$.fieldErrors.compileLog").value("l.5 \\undefinedmacro"));
    }

    @Test
    void anLlmFailureIs502WithTheProvidersMessage() throws Exception {
        willThrow(new LlmException("LLM endpoint returned 401: Incorrect API key provided"))
                .given(exportService).compilePdf(any());

        mockMvc.perform(post("/api/runs/{id}/pdf", RUN_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("LLM endpoint returned 401: Incorrect API key provided"));
    }

    @Test
    void dockerBeingStoppedIs503WithTheFix() throws Exception {
        willThrow(new ServiceUnavailableException("Docker is not running. Start Docker Desktop and try again."))
                .given(exportService).compilePdf(any());

        mockMvc.perform(post("/api/runs/{id}/pdf", RUN_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Docker is not running. Start Docker Desktop and try again."));
    }

    @Test
    void somethingGenuinelyUnexpectedStillFallsBackTo500WithoutLeakingDetail() throws Exception {
        willThrow(new IllegalStateException("internal detail that must not leak"))
                .given(exportService).compilePdf(any());

        mockMvc.perform(post("/api/runs/{id}/pdf", RUN_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }
}
