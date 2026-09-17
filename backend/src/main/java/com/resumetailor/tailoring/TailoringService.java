package com.resumetailor.tailoring;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.jd.JobDescription;
import com.resumetailor.jd.JobDescriptionRepository;
import com.resumetailor.jd.JobDescriptionService;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.keyword.KeywordMatch;
import com.resumetailor.keyword.KeywordMatcher;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.resume.Resume;
import com.resumetailor.resume.ResumeParser;
import com.resumetailor.resume.ResumeRepository;
import com.resumetailor.resume.ResumeService;
import com.resumetailor.tailoring.TailoringDtos.CreateRunRequest;
import com.resumetailor.tailoring.TailoringDtos.KeywordResponse;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringDtos.RunSummary;
import com.resumetailor.tailoring.TailoringDtos.SuggestionResponse;
import com.resumetailor.user.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TailoringService {

    private final TailoringRunRepository runRepository;
    private final RunChangeRepository changeRepository;
    private final RunSuggestionRepository suggestionRepository;
    private final RunKeywordRepository keywordRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeService resumeService;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final JobDescriptionService jobDescriptionService;
    private final LlmProfileService llmProfileService;
    private final KeywordMatcher keywordMatcher;
    private final TailoringRunner runner;
    private final CurrentUserProvider currentUser;

    @Transactional
    public RunDetail createRun(CreateRunRequest request) {
        Resume resume = resumeService.requireForTailoring(request.resumeId());

        JobDescription jd;
        if (request.jobDescriptionId() != null) {
            jd = jobDescriptionService.require(request.jobDescriptionId());
        } else if (request.jdText() != null && !request.jdText().isBlank()) {
            jd = jobDescriptionService.findOrCreate(
                    request.company(), request.role(), request.sourceUrl(), request.jdText().strip());
        } else {
            throw new BadRequestException("Paste a job description to tailor against");
        }

        // Resolve the profile now so a missing LLM config fails the request, not the run.
        UUID profileId = llmProfileService.resolveProfileId(request.llmProfileId());

        TailoringRun run = new TailoringRun();
        run.setOwnerId(currentUser.currentUserId());
        run.setResumeId(resume.getId());
        run.setJobDescriptionId(jd.getId());
        run.setLlmProfileId(profileId);
        run.setStatus(RunStatus.PENDING);
        run.setFormat(resume.getFormat());
        run.setOriginalSource(resume.getSourceText());
        run.setOriginalBody(resume.getBodyText());
        run.setPreamble(resume.getPreamble());
        run.setDocumentTail(resume.getDocumentTail());
        TailoringRun saved = runRepository.save(run);

        // Start work only once the row is actually committed, otherwise the async thread
        // can look for a run that is not visible to it yet.
        UUID runId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.run(runId);
            }
        });
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public Page<RunSummary> listRuns(Pageable pageable) {
        Page<TailoringRun> page =
                runRepository.findAllByOwnerIdOrderByCreatedAtDesc(currentUser.currentUserId(), pageable);

        Map<UUID, String> resumeNames = resumeNames(page.getContent());
        Map<UUID, JobDescription> jds = jobDescriptions(page.getContent());

        return page.map(run -> {
            JobDescription jd = jds.get(run.getJobDescriptionId());
            return new RunSummary(
                    run.getId(),
                    run.getStatus(),
                    jd != null ? jd.getCompany() : null,
                    jd != null ? jd.getRole() : null,
                    resumeNames.get(run.getResumeId()),
                    run.getFormat(),
                    run.getMatchScore(),
                    run.getCreatedAt(),
                    run.getFinishedAt());
        });
    }

    @Transactional(readOnly = true)
    public RunDetail getRun(UUID id) {
        return toDetail(require(id));
    }

    @Transactional
    public void deleteRun(UUID id) {
        runRepository.delete(require(id));
    }

    /**
     * Accepting or rejecting a suggestion rebuilds the tailored document from the
     * model's pristine output plus whatever is currently accepted. Nothing is mutated
     * in place, so every decision is reversible and un-accepting really does remove the
     * text again.
     */
    @Transactional
    public RunDetail updateSuggestion(UUID runId, UUID suggestionId, SuggestionStatus status) {
        TailoringRun run = require(runId);
        RunSuggestion suggestion = suggestionRepository.findByIdAndRunId(suggestionId, runId)
                .orElseThrow(() -> NotFoundException.of("Suggestion", suggestionId));

        suggestion.setStatus(status);
        suggestionRepository.save(suggestion);

        rebuildTailoredDocument(run);
        return toDetail(runRepository.save(run));
    }

    /** The document as it stands: model output plus every accepted suggestion. */
    @Transactional(readOnly = true)
    public String effectiveBody(TailoringRun run) {
        return applyAccepted(run, suggestionRepository
                .findAllByRunIdAndStatusOrderByOrdinalAsc(run.getId(), SuggestionStatus.ACCEPTED));
    }

    private void rebuildTailoredDocument(TailoringRun run) {
        if (run.getTailoredBody() == null) {
            return;
        }
        String body = applyAccepted(run, suggestionRepository
                .findAllByRunIdAndStatusOrderByOrdinalAsc(run.getId(), SuggestionStatus.ACCEPTED));
        run.setTailoredSource(ResumeParser.reassemble(run.getPreamble(), body, run.getDocumentTail()));

        // Accepting a skill can legitimately raise the match score, so re-evaluate it.
        List<RunKeyword> stored = keywordRepository.findAllByRunIdOrderByOrdinalAsc(run.getId());
        if (!stored.isEmpty()) {
            List<JdKeyword> keywords = stored.stream()
                    .map(k -> new JdKeyword(k.getKeyword(), k.getImportance()))
                    .toList();
            List<KeywordMatch> matches = keywordMatcher.match(keywords, run.getOriginalBody(), body);
            for (int i = 0; i < stored.size(); i++) {
                stored.get(i).setPresentInTailored(matches.get(i).presentInTailored());
            }
            keywordRepository.saveAll(stored);
            run.setMatchScore(keywordMatcher.score(matches));
        }
    }

    private String applyAccepted(TailoringRun run, List<RunSuggestion> accepted) {
        String body = run.getTailoredBody();
        if (body == null) {
            return "";
        }
        for (RunSuggestion suggestion : accepted) {
            body = SuggestionSplicer.splice(
                    body, run.getFormat(), suggestion.getKind(),
                    suggestion.getTargetSection(), suggestion.getContent());
        }
        return body;
    }

    private TailoringRun require(UUID id) {
        return runRepository.findByIdAndOwnerId(id, currentUser.currentUserId())
                .orElseThrow(() -> NotFoundException.of("Run", id));
    }

    RunDetail toDetail(TailoringRun run) {
        List<RunChange> changes = changeRepository.findAllByRunIdOrderByOrdinalAsc(run.getId());
        List<RunSuggestion> suggestions = suggestionRepository.findAllByRunIdOrderByOrdinalAsc(run.getId());
        List<RunKeyword> keywords = keywordRepository.findAllByRunIdOrderByOrdinalAsc(run.getId());

        String tailoredBody = applyAccepted(run, suggestions.stream()
                .filter(s -> s.getStatus() == SuggestionStatus.ACCEPTED)
                .toList());

        JobDescription jd = jobDescriptionRepository.findById(run.getJobDescriptionId()).orElse(null);
        String resumeName = resumeRepository.findById(run.getResumeId()).map(Resume::getName).orElse(null);

        return new RunDetail(
                run.getId(),
                run.getStatus(),
                run.getFormat(),
                jd != null ? jd.getCompany() : null,
                jd != null ? jd.getRole() : null,
                run.getResumeId(),
                resumeName,
                run.getJobDescriptionId(),
                jd != null ? jd.getRawText() : null,
                run.getOriginalSource(),
                run.getTailoredBody() == null
                        ? null
                        : ResumeParser.reassemble(run.getPreamble(), tailoredBody, run.getDocumentTail()),
                run.getModelUsed(),
                run.getPromptTokens(),
                run.getCompletionTokens(),
                run.getMatchScore(),
                run.getErrorMessage(),
                SectionDiffBuilder.build(run.getOriginalBody(), tailoredBody, run.getFormat(), changes),
                suggestions.stream()
                        .map(s -> new SuggestionResponse(
                                s.getId(), s.getKind(), s.getTargetSection(),
                                s.getContent(), s.getRationale(), s.getStatus()))
                        .toList(),
                keywords.stream()
                        .map(k -> new KeywordResponse(
                                k.getKeyword(), k.getImportance(),
                                k.isPresentInOriginal(), k.isPresentInTailored()))
                        .toList(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }

    private Map<UUID, String> resumeNames(List<TailoringRun> runs) {
        Set<UUID> ids = runs.stream().map(TailoringRun::getResumeId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        resumeRepository.findAllById(ids).forEach(resume -> names.put(resume.getId(), resume.getName()));
        return names;
    }

    private Map<UUID, JobDescription> jobDescriptions(List<TailoringRun> runs) {
        Set<UUID> ids = runs.stream().map(TailoringRun::getJobDescriptionId).collect(Collectors.toSet());
        Map<UUID, JobDescription> byId = new HashMap<>();
        jobDescriptionRepository.findAllById(ids).forEach(jd -> byId.put(jd.getId(), jd));
        return byId;
    }
}
