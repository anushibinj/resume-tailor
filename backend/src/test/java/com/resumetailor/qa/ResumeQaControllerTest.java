package com.resumetailor.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.config.CorsProperties;
import com.resumetailor.qa.ResumeQaDtos.AskQuestionRequest;
import com.resumetailor.qa.ResumeQaDtos.ResumeQuestionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code addFilters = false}: see the note on {@code ResumeControllerTest}. */
@WebMvcTest(ResumeQaController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class ResumeQaControllerTest {

    private static final UUID RESUME_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID QUESTION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ResumeQaService resumeQaService;

    private static ResumeQuestionResponse completed() {
        return new ResumeQuestionResponse(
                QUESTION_ID, RESUME_ID, "What are my core strengths?",
                "You have strong backend experience in Java and distributed systems.",
                QuestionStatus.COMPLETED, null, "gpt-4o-mini", Instant.now());
    }

    @Test
    void asksAQuestionAndReturns201WithTheAnswer() throws Exception {
        given(resumeQaService.ask(eq(RESUME_ID), any())).willReturn(completed());

        mockMvc.perform(post("/api/resumes/{resumeId}/questions", RESUME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AskQuestionRequest("What are my core strengths?", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.answer").value(
                        "You have strong backend experience in Java and distributed systems."));
    }

    @Test
    void rejectsABlankQuestion() throws Exception {
        mockMvc.perform(post("/api/resumes/{resumeId}/questions", RESUME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskQuestionRequest("   ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.question").exists());
    }

    @Test
    void mapsAQuestionOnAMissingResumeTo404() throws Exception {
        given(resumeQaService.ask(eq(RESUME_ID), any())).willThrow(NotFoundException.of("Resume", RESUME_ID));

        mockMvc.perform(post("/api/resumes/{resumeId}/questions", RESUME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskQuestionRequest("q", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsTheQuestionHistoryForTheResume() throws Exception {
        given(resumeQaService.history(eq(RESUME_ID), any()))
                .willReturn(new PageImpl<>(List.of(completed())));

        mockMvc.perform(get("/api/resumes/{resumeId}/questions", RESUME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].question").value("What are my core strengths?"));
    }

    @Test
    void deletesAQuestionAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/resumes/{resumeId}/questions/{id}", RESUME_ID, QUESTION_ID))
                .andExpect(status().isNoContent());

        verify(resumeQaService).delete(RESUME_ID, QUESTION_ID);
    }

    @Test
    void mapsDeletingAMissingQuestionTo404() throws Exception {
        org.mockito.BDDMockito.willThrow(NotFoundException.of("Question", QUESTION_ID))
                .given(resumeQaService).delete(RESUME_ID, QUESTION_ID);

        mockMvc.perform(delete("/api/resumes/{resumeId}/questions/{id}", RESUME_ID, QUESTION_ID))
                .andExpect(status().isNotFound());
    }
}
