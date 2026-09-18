package com.resumetailor.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.config.CorsProperties;
import com.resumetailor.resume.ResumeDtos.ResumeDetail;
import com.resumetailor.resume.ResumeDtos.SaveResumeRequest;
import com.resumetailor.resume.ResumeDtos.SectionSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP contract the frontend depends on. No database involved.
 *
 * <p>{@code addFilters = false} skips the real {@code SecurityConfig} filter chain: this
 * test is about the controller/service contract, not auth, and every request here would
 * otherwise need a bearer token just to reach the assertions below.
 */
@WebMvcTest(ResumeController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class ResumeControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ResumeService resumeService;

    private static ResumeDetail detail() {
        return new ResumeDetail(
                ID, "Backend-leaning", ResumeFormat.LATEX, true,
                "\\documentclass{article}\\begin{document}x\\end{document}",
                "\\documentclass{article}\\begin{document}", "x", "\\end{document}",
                List.of(new SectionSummary("Experience", 1, 120)),
                Instant.now(), Instant.now());
    }

    @Test
    void returnsResumeDetailIncludingTheSplitPreamble() throws Exception {
        given(resumeService.get(ID)).willReturn(detail());

        mockMvc.perform(get("/api/resumes/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Backend-leaning"))
                .andExpect(jsonPath("$.format").value("LATEX"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.preamble").value("\\documentclass{article}\\begin{document}"))
                .andExpect(jsonPath("$.sections[0].title").value("Experience"));
    }

    @Test
    void createsAResumeAndReturns201() throws Exception {
        given(resumeService.create(any())).willReturn(detail());

        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SaveResumeRequest("Backend-leaning", "# Jane", null, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void rejectsAResumeWithNoNameAndNamesTheOffendingField() throws Exception {
        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SaveResumeRequest("  ", "# Jane", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void mapsAMissingResumeTo404() throws Exception {
        given(resumeService.get(eq(ID))).willThrow(NotFoundException.of("Resume", ID));

        mockMvc.perform(get("/api/resumes/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume " + ID + " was not found"));
    }

    @Test
    void surfacesDuplicateNamesAsAReadable400() throws Exception {
        willThrow(new BadRequestException("A resume named 'Backend-leaning' already exists"))
                .given(resumeService).create(any());

        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SaveResumeRequest("Backend-leaning", "# Jane", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A resume named 'Backend-leaning' already exists"));
    }
}
