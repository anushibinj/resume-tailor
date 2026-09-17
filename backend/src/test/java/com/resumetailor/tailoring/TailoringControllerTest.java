package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.common.BadRequestException;
import com.resumetailor.config.CorsProperties;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.tailoring.TailoringDtos.CreateRunRequest;
import com.resumetailor.tailoring.TailoringDtos.KeywordResponse;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringDtos.SectionDiff;
import com.resumetailor.tailoring.TailoringDtos.SuggestionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TailoringController.class)
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class TailoringControllerTest {

    private static final UUID RUN_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID SUGGESTION_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TailoringService tailoringService;

    private static RunDetail detail(RunStatus status) {
        return new RunDetail(
                RUN_ID, status, ResumeFormat.MARKDOWN, "Acme", "Backend Engineer",
                UUID.randomUUID(), "Backend-leaning", UUID.randomUUID(), "We need Kubernetes",
                "## Experience\n- Built billing",
                status == RunStatus.COMPLETED ? "## Experience\n- Shipped billing on Kubernetes" : null,
                "gpt-4o-mini", 1200, 800, 75, null,
                List.of(new SectionDiff("Experience", 2, "- Built billing",
                        "- Shipped billing on Kubernetes", "REWORDED", "Surfaces Kubernetes")),
                List.of(new SuggestionResponse(SUGGESTION_ID, SuggestionKind.SKILL, "Skills",
                        "Terraform", "The posting requires it", SuggestionStatus.PROPOSED)),
                List.of(new KeywordResponse("Kubernetes", KeywordImportance.REQUIRED, false, true)),
                Instant.now(), Instant.now(), Instant.now());
    }

    @Test
    void acceptsARunAndReturns202SoTheUiCanPoll() throws Exception {
        given(tailoringService.createRun(any())).willReturn(detail(RunStatus.PENDING));

        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRunRequest(
                                null, null, "We need Kubernetes", "Acme", "Backend Engineer", null, null))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(RUN_ID.toString()));
    }

    @Test
    void returnsTheFullRunIncludingDiffKeywordsAndSuggestions() throws Exception {
        given(tailoringService.getRun(RUN_ID)).willReturn(detail(RunStatus.COMPLETED));

        mockMvc.perform(get("/api/runs/{id}", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(75))
                .andExpect(jsonPath("$.sections[0].title").value("Experience"))
                .andExpect(jsonPath("$.sections[0].changeType").value("REWORDED"))
                .andExpect(jsonPath("$.keywords[0].presentInOriginal").value(false))
                .andExpect(jsonPath("$.keywords[0].presentInTailored").value(true))
                // Suggestions must arrive unapplied: the user decides, not the model.
                .andExpect(jsonPath("$.suggestions[0].status").value("PROPOSED"));
    }

    @Test
    void acceptingASuggestionReturnsTheRebuiltRun() throws Exception {
        given(tailoringService.updateSuggestion(RUN_ID, SUGGESTION_ID, SuggestionStatus.ACCEPTED))
                .willReturn(detail(RunStatus.COMPLETED));

        mockMvc.perform(patch("/api/runs/{id}/suggestions/{sid}", RUN_ID, SUGGESTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tailoredSource").exists());
    }

    @Test
    void rejectsASuggestionUpdateWithNoStatus() throws Exception {
        mockMvc.perform(patch("/api/runs/{id}/suggestions/{sid}", RUN_ID, SUGGESTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explainsWhenNoJobDescriptionWasSupplied() throws Exception {
        willThrow(new BadRequestException("Paste a job description to tailor against"))
                .given(tailoringService).createRun(any());

        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Paste a job description to tailor against"));
    }
}
