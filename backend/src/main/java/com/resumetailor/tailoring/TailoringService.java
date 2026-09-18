package com.resumetailor.tailoring;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.export.ArtifactStore;
import com.resumetailor.jd.JobDescription;
import com.resumetailor.jd.JobDescriptionRepository;
import com.resumetailor.jd.JobDescriptionService;
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
import java.util.HashSet;
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
    private final TailoringRunStore store;
    private final TailoringRunner runner;
    private final CurrentUserProvider currentUser;
    private final ArtifactStore artifactStore;

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
        run.setGapsStatus(GapsStatus.PENDING);
        run.setFormat(resume.getFormat());
        run.setOriginalSource(resume.getSourceText());
        run.setOriginalBody(resume.getBodyText());
        run.setPreamble(resume.getPreamble());
        run.setDocumentTail(resume.getDocumentTail());
        TailoringRun saved = runRepository.save(run);

        // Start work only once the row is actually committed, otherwise the async thread
        // can look for a run that is not visible to it yet.
        UUID runId = saved.getId();
        afterCommit(() -> runner.run(runId));
        return toDetail(saved);
    }

    /**
     * Re-runs the requirement check against the document as it currently stands.
     *
     * <p>Needed for runs whose coverage came from the retired keyword matcher, and after a
     * gap check fails. Additions the user already accepted are kept.
     */
    @Transactional
    public RunDetail requestGapRecheck(UUID id) {
        TailoringRun run = require(id);
        if (run.getStatus() != RunStatus.COMPLETED || run.getTailoredBody() == null) {
            throw new BadRequestException("This run has not produced a tailored resume yet");
        }
        if (run.getGapsStatus() == GapsStatus.RUNNING) {
            throw new BadRequestException("A check is already running for this run");
        }
        run.setGapsStatus(GapsStatus.PENDING);
        run.setGapsError(null);
        runRepository.save(run);

        UUID runId = run.getId();
        afterCommit(() -> runner.analyzeGaps(runId));
        return toDetail(run);
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
                    displayScore(run),
                    run.getCreatedAt(),
                    run.getFinishedAt());
        });
    }

    @Transactional(readOnly = true)
    public RunDetail getRun(UUID id) {
        return toDetail(require(id));
    }

    /** Removes the run's own DB row (its children cascade) and the compiled PDF it left on disk. */
    @Transactional
    public void deleteRun(UUID id) {
        TailoringRun run = require(id);
        artifactStore.deleteFilesForRun(run.getId());
        runRepository.delete(run);
    }

    /**
     * Accepting or removing an addition rebuilds the document from the model's pristine
     * output plus whatever is currently accepted. Nothing is edited in place, so every
     * decision is reversible and removing an addition really does take the text back out.
     */
    @Transactional
    public RunDetail updateSuggestion(UUID runId, UUID suggestionId, SuggestionStatus status) {
        TailoringRun run = require(runId);
        RunSuggestion suggestion = suggestionRepository.findByIdAndRunId(suggestionId, runId)
                .orElseThrow(() -> NotFoundException.of("Suggestion", suggestionId));

        suggestion.setStatus(status);
        suggestionRepository.save(suggestion);

        if (run.getTailoredBody() != null) {
            run.setTailoredSource(ResumeParser.reassemble(
                    run.getPreamble(), effectiveBody(run), run.getDocumentTail()));
            runRepository.save(run);
        }
        // Accepting a gap closes it, so the score moves without another model call.
        store.recomputeScore(runId);
        return toDetail(run);
    }

    /** The model's rewrite plus every addition the user has accepted. */
    private String effectiveBody(TailoringRun run) {
        String body = run.getTailoredBody();
        if (body == null) {
            return "";
        }
        for (RunSuggestion accepted : suggestionRepository
                .findAllByRunIdAndStatusOrderByOrdinalAsc(run.getId(), SuggestionStatus.ACCEPTED)) {
            body = AdditionApplier.apply(body, run.getFormat(), accepted);
        }
        return body;
    }

    private TailoringRun require(UUID id) {
        return runRepository.findByIdAndOwnerId(id, currentUser.currentUserId())
                .orElseThrow(() -> NotFoundException.of("Run", id));
    }

    /** Coverage from the retired matcher is not shown as a number; the UI offers a re-check. */
    private static Integer displayScore(TailoringRun run) {
        return run.getGapsStatus() == GapsStatus.COMPLETED ? run.getMatchScore() : null;
    }

    RunDetail toDetail(TailoringRun run) {
        List<RunChange> changes = changeRepository.findAllByRunIdOrderByOrdinalAsc(run.getId());
        List<RunSuggestion> suggestions = suggestionRepository.findAllByRunIdOrderByOrdinalAsc(run.getId());
        Map<UUID, RunSuggestion> suggestionsById = suggestions.stream()
                .collect(Collectors.toMap(RunSuggestion::getId, s -> s));

        String tailoredBody = effectiveBody(run);
        boolean gapsUsable = run.getGapsStatus() == GapsStatus.COMPLETED;

        List<KeywordResponse> keywords = List.of();
        Set<UUID> linked = new HashSet<>();
        if (gapsUsable) {
            keywords = keywordRepository.findAllByRunIdOrderByOrdinalAsc(run.getId()).stream()
                    .map(keyword -> {
                        RunSuggestion addition = keyword.getSuggestionId() == null
                                ? null
                                : suggestionsById.get(keyword.getSuggestionId());
                        if (addition != null) {
                            linked.add(addition.getId());
                        }
                        return new KeywordResponse(
                                keyword.getKeyword(),
                                keyword.getImportance(),
                                keyword.isCovered(),
                                keyword.getEvidence(),
                                addition == null ? null : toResponse(addition));
                    })
                    .toList();
        }

        List<SuggestionResponse> other = suggestions.stream()
                .filter(suggestion -> !linked.contains(suggestion.getId()))
                .map(TailoringService::toResponse)
                .toList();

        JobDescription jd = jobDescriptionRepository.findById(run.getJobDescriptionId()).orElse(null);
        String resumeName = resumeRepository.findById(run.getResumeId()).map(Resume::getName).orElse(null);

        return new RunDetail(
                run.getId(),
                run.getStatus(),
                run.getGapsStatus(),
                run.getGapsError(),
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
                displayScore(run),
                run.getErrorMessage(),
                SectionDiffBuilder.build(run.getOriginalBody(), tailoredBody, run.getFormat(), changes),
                keywords,
                other,
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }

    private static SuggestionResponse toResponse(RunSuggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getKind(),
                suggestion.getKeyword(),
                suggestion.getTargetSection(),
                suggestion.getContent(),
                suggestion.getRationale(),
                suggestion.getStatus());
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
