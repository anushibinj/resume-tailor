package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.common.BadRequestException;
import com.resumetailor.config.CorsProperties;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.tailoring.TailoringDtos.CreateRunRequest;
import com.resumetailor.tailoring.TailoringDtos.KeywordResponse;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringDtos.RunSummary;
import com.resumetailor.tailoring.TailoringDtos.SectionDiff;
import com.resumetailor.tailoring.TailoringDtos.SuggestionResponse;
import com.resumetailor.tailoring.TailoringDtos.SummaryResponse;
import com.resumetailor.tailoring.TailoringDtos.SummaryVariantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code addFilters = false}: see the note on {@code ResumeControllerTest}. */
@WebMvcTest(TailoringController.class)
@AutoConfigureMockMvc(addFilters = false)
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
        return detail(status, status == RunStatus.COMPLETED ? GapsStatus.COMPLETED : GapsStatus.PENDING);
    }

    private static RunDetail detail(RunStatus status, GapsStatus gapsStatus) {
        SuggestionResponse addition = new SuggestionResponse(
                SUGGESTION_ID, SuggestionKind.SKILL, "Terraform", "Skills", "Terraform",
                null, SuggestionStatus.PROPOSED);
        return new RunDetail(
                RUN_ID, status, gapsStatus, null, ResumeFormat.MARKDOWN, "Acme", "Backend Engineer",
                UUID.randomUUID(), "Backend-leaning", UUID.randomUUID(), "We need Kubernetes",
                "## Experience\n- Built billing",
                status == RunStatus.COMPLETED ? "## Experience\n- Shipped billing on Kubernetes" : null,
                "gpt-4o-mini", 1200, 800, 75, null,
                List.of(new SectionDiff("Experience", 2, "- Built billing",
                        "- Shipped billing on Kubernetes", "REWORDED", "Surfaces Kubernetes")),
                List.of(
                        new KeywordResponse("Kubernetes", KeywordImportance.REQUIRED, true,
                                "Shipped billing on Kubernetes", null, null),
                        new KeywordResponse("Terraform", KeywordImportance.REQUIRED, false, null, addition,
                                "Infrastructure as code: define cloud resources in files.")),
                List.of(),
                new SummaryResponse(SummaryStatus.COMPLETED, null, 5, List.of(
                        new SummaryVariantResponse(4, "Short."),
                        new SummaryVariantResponse(5, "Medium."))),
                false, null, null,
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
    void returnsTheFullRunWithCoverageAndTheAdditionOfferedForEachGap() throws Exception {
        given(tailoringService.getRun(RUN_ID)).willReturn(detail(RunStatus.COMPLETED));

        mockMvc.perform(get("/api/runs/{id}", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(75))
                .andExpect(jsonPath("$.gapsStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.sections[0].title").value("Experience"))
                .andExpect(jsonPath("$.sections[0].changeType").value("REWORDED"))
                // Covered requirements carry the quote the judgment rests on.
                .andExpect(jsonPath("$.keywords[0].covered").value(true))
                .andExpect(jsonPath("$.keywords[0].evidence").value("Shipped billing on Kubernetes"))
                .andExpect(jsonPath("$.keywords[0].addition").doesNotExist())
                .andExpect(jsonPath("$.keywords[0].description").doesNotExist())
                .andExpect(jsonPath("$.keywords[1].description")
                        .value("Infrastructure as code: define cloud resources in files."))
                // A gap always arrives with something the user can add in one click.
                .andExpect(jsonPath("$.keywords[1].covered").value(false))
                .andExpect(jsonPath("$.keywords[1].addition.content").value("Terraform"))
                // Nothing is applied until the user says so.
                .andExpect(jsonPath("$.keywords[1].addition.status").value("PROPOSED"));
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

    @Test
    void recheckIsAcceptedAndReportsTheCheckAsPending() throws Exception {
        given(tailoringService.requestGapRecheck(RUN_ID))
                .willReturn(detail(RunStatus.COMPLETED, GapsStatus.PENDING));

        mockMvc.perform(post("/api/runs/{id}/gaps", RUN_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gapsStatus").value("PENDING"));
    }

    @Test
    void recheckOnAnUnfinishedRunIsARedable400() throws Exception {
        willThrow(new BadRequestException("This run has not produced a tailored resume yet"))
                .given(tailoringService).requestGapRecheck(RUN_ID);

        mockMvc.perform(post("/api/runs/{id}/gaps", RUN_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This run has not produced a tailored resume yet"));
    }

    @Test
    void returnsTheSummaryLengthsAndWhichOneIsChosen() throws Exception {
        given(tailoringService.getRun(RUN_ID)).willReturn(detail(RunStatus.COMPLETED));

        mockMvc.perform(get("/api/runs/{id}", RUN_ID))
                .andExpect(jsonPath("$.summary.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.selectedLines").value(5))
                .andExpect(jsonPath("$.summary.variants[0].lines").value(4))
                .andExpect(jsonPath("$.summary.variants[1].text").value("Medium."));
    }

    @Test
    void choosingASummaryLengthReturnsTheRebuiltRun() throws Exception {
        given(tailoringService.selectSummaryLines(RUN_ID, 4)).willReturn(detail(RunStatus.COMPLETED));

        mockMvc.perform(put("/api/runs/{id}/summary", RUN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tailoredSource").exists());
    }

    @Test
    void onlyLengthsOnTheSliderCanBeChosen() throws Exception {
        for (String body : new String[]{"{\"lines\":3}", "{\"lines\":8}", "{}"}) {
            mockMvc.perform(put("/api/runs/{id}/summary", RUN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        then(tailoringService).should(org.mockito.Mockito.never()).selectSummaryLines(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void generatingSummaryLengthsIsAcceptedAndReportedAsPending() throws Exception {
        given(tailoringService.requestSummary(RUN_ID)).willReturn(detail(RunStatus.COMPLETED));

        mockMvc.perform(post("/api/runs/{id}/summary", RUN_ID))
                .andExpect(status().isAccepted());
    }

    @Test
    void deletingARunReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/runs/{id}", RUN_ID))
                .andExpect(status().isNoContent());

        then(tailoringService).should().deleteRun(RUN_ID);
    }

    @Test
    void listPassesTheAppliedFilterThrough() throws Exception {
        given(tailoringService.listRuns(any(), any())).willReturn(new PageImpl<RunSummary>(List.of()));

        mockMvc.perform(get("/api/runs").param("applied", "true"))
                .andExpect(status().isOk());

        then(tailoringService).should().listRuns(Boolean.TRUE, PageRequest.of(0, 20));
    }

    @Test
    void listWithNoAppliedParamShowsEveryRun() throws Exception {
        given(tailoringService.listRuns(any(), any())).willReturn(new PageImpl<RunSummary>(List.of()));

        mockMvc.perform(get("/api/runs"))
                .andExpect(status().isOk());

        then(tailoringService).should().listRuns(null, PageRequest.of(0, 20));
    }

    @Test
    void marksARunAppliedAndRecordsWhen() throws Exception {
        Instant appliedAt = Instant.now();
        given(tailoringService.setApplied(RUN_ID, true))
                .willReturn(new TailoringDtos.RunApplicationResponse(RUN_ID, true, appliedAt, null));

        mockMvc.perform(patch("/api/runs/{id}/applied", RUN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applied\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.appliedAt").exists());
    }

    @Test
    void rejectsAnAppliedUpdateWithNoValue() throws Exception {
        mockMvc.perform(patch("/api/runs/{id}/applied", RUN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        then(tailoringService).should(org.mockito.Mockito.never()).setApplied(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void setsTheApplicationLinkSoItCanBeReopenedLater() throws Exception {
        String link = "https://boards.greenhouse.io/acme/jobs/123";
        given(tailoringService.setApplicationLink(RUN_ID, link))
                .willReturn(new TailoringDtos.RunApplicationResponse(RUN_ID, false, null, link));

        mockMvc.perform(put("/api/runs/{id}/application-link", RUN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationLink\":\"" + link + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationLink").value(link));
    }
}
