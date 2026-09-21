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
import com.resumetailor.resume.ResumeRepository;
import com.resumetailor.user.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single-shot Q&A against a resume: one question in, one answer out, never a
 * conversation. Each question is judged from the resume alone -- earlier questions and
 * answers are never replayed back to the model, so the history kept here is a log the user
 * can browse, not context a later question builds on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeQaService {

    private final ResumeQuestionRepository repository;
    private final ResumeRepository resumeRepository;
    private final LlmProfileService llmProfileService;
    private final OpenAiCompatibleClient client;
    private final CurrentUserProvider currentUser;

    /**
     * Answers synchronously, unlike a tailoring run: a single question is a quick round
     * trip, not a job worth polling for. A failed LLM call still leaves a row in the
     * history -- with the error, not an answer -- rather than losing the question asked.
     */
    @Transactional
    public ResumeQuestionResponse ask(UUID resumeId, AskQuestionRequest request) {
        UUID ownerId = currentUser.currentUserId();
        Resume resume = resumeRepository.findByIdAndOwnerId(resumeId, ownerId)
                .orElseThrow(() -> NotFoundException.of("Resume", resumeId));
        String question = request.question().strip();

        UUID profileId = llmProfileService.resolveProfileId(request.llmProfileId());
        LlmSettings settings = llmProfileService.resolveSettings(profileId);

        ResumeQuestion record = new ResumeQuestion();
        record.setOwnerId(ownerId);
        record.setResumeId(resume.getId());
        record.setLlmProfileId(profileId);
        record.setQuestion(question);

        try {
            // Only the body ever reaches a model -- the same rule the tailoring pipeline
            // follows: a LaTeX preamble is markup for the compiler, never prompt content.
            LlmChatResult result = client.chat(
                    settings, QaPrompts.SYSTEM, QaPrompts.user(resume.getBodyText(), question), false);
            record.setAnswer(result.content().strip());
            record.setModelUsed(result.model());
            record.setPromptTokens(result.promptTokens());
            record.setCompletionTokens(result.completionTokens());
            record.setStatus(QuestionStatus.COMPLETED);
        } catch (LlmException ex) {
            log.warn("Question on resume {} failed: {}", resumeId, ex.getMessage());
            record.setStatus(QuestionStatus.FAILED);
            record.setErrorMessage(ex.getMessage());
        }

        return toResponse(repository.save(record));
    }

    @Transactional(readOnly = true)
    public Page<ResumeQuestionResponse> history(UUID resumeId, Pageable pageable) {
        UUID ownerId = currentUser.currentUserId();
        if (!resumeRepository.existsByIdAndOwnerId(resumeId, ownerId)) {
            throw NotFoundException.of("Resume", resumeId);
        }
        return repository.findAllByOwnerIdAndResumeIdOrderByCreatedAtDesc(ownerId, resumeId, pageable)
                .map(ResumeQaService::toResponse);
    }

    /**
     * Removes one entry from the history log. Nothing else depends on it -- questions are
     * never replayed as context -- so there is nothing to cascade or recompute.
     */
    @Transactional
    public void delete(UUID resumeId, UUID questionId) {
        UUID ownerId = currentUser.currentUserId();
        ResumeQuestion question = repository.findByIdAndOwnerIdAndResumeId(questionId, ownerId, resumeId)
                .orElseThrow(() -> NotFoundException.of("Question", questionId));
        repository.delete(question);
    }

    private static ResumeQuestionResponse toResponse(ResumeQuestion question) {
        return new ResumeQuestionResponse(
                question.getId(),
                question.getResumeId(),
                question.getQuestion(),
                question.getAnswer(),
                question.getStatus(),
                question.getErrorMessage(),
                question.getModelUsed(),
                question.getCreatedAt());
    }
}
