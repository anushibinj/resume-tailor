package com.resumetailor.qa;

import com.resumetailor.common.NotFoundException;
import com.resumetailor.llm.LlmChatResult;
import com.resumetailor.llm.LlmException;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.llm.LlmSettings;
import com.resumetailor.llm.OpenAiCompatibleClient;
import com.resumetailor.qa.ResumeQaDtos.AskQuestionRequest;
import com.resumetailor.qa.ResumeQaDtos.ResumeQuestionResponse;
import com.resumetailor.resume.Resume;
import com.resumetailor.resume.ResumeFormat;
import com.resumetailor.resume.ResumeRepository;
import com.resumetailor.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one rule that matters most here is the same one the tailoring pipeline follows:
 * only the resume body reaches a prompt, never the LaTeX preamble.
 */
class ResumeQaServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID RESUME_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    private ResumeQuestionRepository repository;
    private ResumeRepository resumeRepository;
    private LlmProfileService llmProfileService;
    private OpenAiCompatibleClient client;
    private ResumeQaService service;

    @BeforeEach
    void setUp() {
        repository = mock(ResumeQuestionRepository.class);
        resumeRepository = mock(ResumeRepository.class);
        llmProfileService = mock(LlmProfileService.class);
        client = mock(OpenAiCompatibleClient.class);
        CurrentUserProvider currentUser = () -> OWNER_ID;
        service = new ResumeQaService(repository, resumeRepository, llmProfileService, client, currentUser);

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(llmProfileService.resolveProfileId(any())).thenReturn(PROFILE_ID);
        when(llmProfileService.resolveSettings(PROFILE_ID))
                .thenReturn(new LlmSettings("https://api.example.com", "key", "gpt-x", BigDecimal.ONE, 500));
    }

    private static Resume resume() {
        Resume resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setOwnerId(OWNER_ID);
        resume.setName("Backend-leaning");
        resume.setFormat(ResumeFormat.LATEX);
        resume.setSourceText("\\documentclass{article}\\begin{document}Built billing in Java\\end{document}");
        resume.setPreamble("\\documentclass{article}\\begin{document}");
        resume.setBodyText("Built billing in Java");
        resume.setDocumentTail("\\end{document}");
        return resume;
    }

    @Test
    void sendsOnlyTheResumeBodyToTheModelNeverThePreamble() {
        when(resumeRepository.findByIdAndOwnerId(RESUME_ID, OWNER_ID)).thenReturn(Optional.of(resume()));
        when(client.chat(any(), any(), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(new LlmChatResult("You have Java experience.", "gpt-x", 100, 20));

        service.ask(RESUME_ID, new AskQuestionRequest("What languages do I know?", null));

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(any(), any(), userPrompt.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertThat(userPrompt.getValue()).contains("Built billing in Java");
        assertThat(userPrompt.getValue()).doesNotContain("\\documentclass");
        assertThat(userPrompt.getValue()).doesNotContain("\\begin{document}");
    }

    @Test
    void savesACompletedAnswerWithUsageAndModel() {
        when(resumeRepository.findByIdAndOwnerId(RESUME_ID, OWNER_ID)).thenReturn(Optional.of(resume()));
        when(client.chat(any(), any(), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(new LlmChatResult("You have Java experience.", "gpt-x", 100, 20));

        ResumeQuestionResponse response = service.ask(RESUME_ID, new AskQuestionRequest("  Sum it up?  ", null));

        assertThat(response.status()).isEqualTo(QuestionStatus.COMPLETED);
        assertThat(response.answer()).isEqualTo("You have Java experience.");
        assertThat(response.modelUsed()).isEqualTo("gpt-x");
        assertThat(response.question()).isEqualTo("Sum it up?");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void aFailedLlmCallIsRecordedInHistoryInsteadOfLosingTheQuestion() {
        when(resumeRepository.findByIdAndOwnerId(RESUME_ID, OWNER_ID)).thenReturn(Optional.of(resume()));
        when(client.chat(any(), any(), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenThrow(new LlmException("LLM endpoint returned 401"));

        ResumeQuestionResponse response = service.ask(RESUME_ID, new AskQuestionRequest("What did I do?", null));

        assertThat(response.status()).isEqualTo(QuestionStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("LLM endpoint returned 401");
        assertThat(response.answer()).isNull();
        verify(repository).save(any());
    }

    @Test
    void refusesToAnswerAboutAResumeTheCallerDoesNotOwn() {
        when(resumeRepository.findByIdAndOwnerId(RESUME_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(RESUME_ID, new AskQuestionRequest("q", null)))
                .isInstanceOf(NotFoundException.class);
        verify(client, never()).chat(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void refusesHistoryForAResumeTheCallerDoesNotOwn() {
        when(resumeRepository.existsByIdAndOwnerId(RESUME_ID, OWNER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.history(RESUME_ID, Pageable.unpaged()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsHistoryNewestFirstForTheOwnedResume() {
        when(resumeRepository.existsByIdAndOwnerId(RESUME_ID, OWNER_ID)).thenReturn(true);
        ResumeQuestion question = new ResumeQuestion();
        question.setOwnerId(OWNER_ID);
        question.setResumeId(RESUME_ID);
        question.setQuestion("q");
        question.setAnswer("a");
        question.setStatus(QuestionStatus.COMPLETED);
        Pageable pageable = Pageable.unpaged();
        Page<ResumeQuestion> page = new PageImpl<>(java.util.List.of(question));
        when(repository.findAllByOwnerIdAndResumeIdOrderByCreatedAtDesc(OWNER_ID, RESUME_ID, pageable))
                .thenReturn(page);

        Page<ResumeQuestionResponse> result = service.history(RESUME_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).answer()).isEqualTo("a");
    }

    @Test
    void deletesAQuestionTheCallerOwnsOnThatResume() {
        UUID questionId = UUID.randomUUID();
        ResumeQuestion question = new ResumeQuestion();
        when(repository.findByIdAndOwnerIdAndResumeId(questionId, OWNER_ID, RESUME_ID))
                .thenReturn(Optional.of(question));

        service.delete(RESUME_ID, questionId);

        verify(repository).delete(question);
    }

    @Test
    void refusesToDeleteAQuestionTheCallerDoesNotOwn() {
        UUID questionId = UUID.randomUUID();
        when(repository.findByIdAndOwnerIdAndResumeId(questionId, OWNER_ID, RESUME_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(RESUME_ID, questionId))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
