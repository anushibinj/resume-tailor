package com.resumetailor.tailoring;

import com.resumetailor.keyword.CoverageScorer;
import com.resumetailor.resume.ResumeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transactional persistence for a run's lifecycle.
 *
 * <p>Separate from {@link TailoringRunner} on purpose: the runner is {@code @Async} and
 * calls these methods across bean boundaries, which is what lets {@code @Transactional}
 * actually apply. Self-invocation inside one bean would bypass the proxy.
 */
@Component
@RequiredArgsConstructor
public class TailoringRunStore {

    private final TailoringRunRepository runRepository;
    private final RunChangeRepository changeRepository;
    private final RunSuggestionRepository suggestionRepository;
    private final RunKeywordRepository keywordRepository;

    @Transactional
    public RunContext markRunning(UUID runId) {
        TailoringRun run = runRepository.findById(runId).orElseThrow();
        run.setStatus(RunStatus.RUNNING);
        run.setGapsStatus(GapsStatus.PENDING);
        run.setStartedAt(Instant.now());
        runRepository.save(run);
        return new RunContext(
                run.getId(),
                run.getJobDescriptionId(),
                run.getLlmProfileId(),
                run.getFormat(),
                run.getOriginalBody(),
                run.getPreamble(),
                run.getDocumentTail());
    }

    @Transactional
    public void markFailed(UUID runId, String message) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(message);
            run.setFinishedAt(Instant.now());
            runRepository.save(run);
        });
    }

    /**
     * Saves the rewrite and hands back what the gap analysis needs.
     *
     * <p>The run is marked COMPLETED here, before gaps are known: the rewrite is the slow,
     * expensive part and is worth showing immediately. Gap analysis then runs on its own
     * status, so failing it leaves the rewrite intact and retryable.
     */
    @Transactional
    public GapContext saveRewrite(UUID runId, TailorOutput output, String modelUsed,
                                  Integer promptTokens, Integer completionTokens) {
        TailoringRun run = runRepository.findById(runId).orElseThrow();

        run.setTailoredBody(output.tailoredBody());
        // The preamble is reattached here, never regenerated -- it was never sent to the model.
        run.setTailoredSource(ResumeParser.reassemble(
                run.getPreamble(), output.tailoredBody(), run.getDocumentTail()));
        run.setModelUsed(modelUsed);
        run.setPromptTokens(promptTokens);
        run.setCompletionTokens(completionTokens);
        run.setMatchScore(null);
        run.setStatus(RunStatus.COMPLETED);
        run.setGapsStatus(GapsStatus.RUNNING);
        run.setGapsError(null);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        // A re-run of the same row replaces its children rather than accumulating them.
        changeRepository.deleteAllByRunId(runId);
        keywordRepository.deleteAllByRunId(runId);
        suggestionRepository.deleteAllByRunId(runId);

        int ordinal = 0;
        for (TailorOutput.ChangeItem item : output.changes()) {
            RunChange change = new RunChange();
            change.setRunId(runId);
            change.setSectionTitle(item.section());
            change.setChangeType(item.changeType());
            change.setRationale(item.rationale());
            change.setOrdinal(ordinal++);
            changeRepository.save(change);
        }

        return new GapContext(
                runId, run.getJobDescriptionId(), run.getLlmProfileId(), run.getFormat(),
                output.tailoredBody(), List.of());
    }

    /** Marks gap analysis as started and gathers the current document plus its additions. */
    @Transactional
    public GapContext beginGapAnalysis(UUID runId) {
        TailoringRun run = runRepository.findById(runId).orElseThrow();
        run.setGapsStatus(GapsStatus.RUNNING);
        run.setGapsError(null);
        runRepository.save(run);

        List<RunSuggestion> accepted = suggestionRepository
                .findAllByRunIdAndStatusOrderByOrdinalAsc(runId, SuggestionStatus.ACCEPTED);

        // Judge the model's pristine rewrite, NOT the document with additions applied. If the
        // added text were present, the model would rightly call the requirement covered, the
        // addition would come back detached from it, and removing the addition would leave the
        // requirement still showing as covered. Passing the additions separately lets the model
        // point at one instead, which keeps accepting and removing reversible.
        return new GapContext(
                runId, run.getJobDescriptionId(), run.getLlmProfileId(), run.getFormat(),
                run.getTailoredBody() == null ? "" : run.getTailoredBody(),
                accepted.stream()
                        .map(s -> new Prompts.ExistingAddition(s.getId().toString(), s.getContent()))
                        .toList());
    }

    /**
     * Replaces the requirement list and its offered additions.
     *
     * <p>Anything the user already accepted survives: it is part of the document the model
     * just judged, and deleting it would silently change what they are about to send.
     */
    @Transactional
    public void saveGaps(UUID runId, GapAnalysisResult result) {
        TailoringRun run = runRepository.findById(runId).orElseThrow();

        keywordRepository.deleteAllByRunId(runId);
        List<RunSuggestion> kept = suggestionRepository
                .findAllByRunIdAndStatusOrderByOrdinalAsc(runId, SuggestionStatus.ACCEPTED);
        suggestionRepository.deleteAllByRunIdAndStatusNot(runId, SuggestionStatus.ACCEPTED);
        Map<UUID, RunSuggestion> keptById = kept.stream()
                .collect(Collectors.toMap(RunSuggestion::getId, Function.identity()));

        int ordinal = 0;
        List<CoverageScorer.Requirement> scored = new ArrayList<>();
        for (GapAnalysisResult.RequirementVerdict verdict : result.verdicts()) {
            RunKeyword keyword = new RunKeyword();
            keyword.setRunId(runId);
            keyword.setKeyword(verdict.requirement().keyword());
            keyword.setImportance(verdict.requirement().importance());
            keyword.setCovered(verdict.covered());
            keyword.setEvidence(verdict.evidence());
            keyword.setOrdinal(ordinal);

            RunSuggestion linked = null;
            if (verdict.addressedBy() != null) {
                linked = keptById.get(verdict.addressedBy());
            } else if (verdict.addition() != null) {
                linked = persistAddition(runId, verdict, ordinal);
            }
            if (linked != null) {
                linked.setKeyword(verdict.requirement().keyword());
                suggestionRepository.save(linked);
                keyword.setSuggestionId(linked.getId());
            }

            keywordRepository.save(keyword);
            scored.add(new CoverageScorer.Requirement(
                    verdict.requirement().importance(),
                    verdict.covered() || (linked != null && linked.getStatus() == SuggestionStatus.ACCEPTED)));
            ordinal++;
        }

        run.setMatchScore(CoverageScorer.score(scored));
        run.setGapsStatus(GapsStatus.COMPLETED);
        run.setGapsError(null);
        runRepository.save(run);
    }

    @Transactional
    public void markGapsFailed(UUID runId, String message) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setGapsStatus(GapsStatus.FAILED);
            run.setGapsError(message);
            runRepository.save(run);
        });
    }

    /** Recomputes the score from stored verdicts plus whichever additions are accepted. */
    @Transactional
    public void recomputeScore(UUID runId) {
        TailoringRun run = runRepository.findById(runId).orElseThrow();
        List<RunKeyword> keywords = keywordRepository.findAllByRunIdOrderByOrdinalAsc(runId);
        if (keywords.isEmpty()) {
            return;
        }
        Map<UUID, SuggestionStatus> statuses = suggestionRepository.findAllByRunIdOrderByOrdinalAsc(runId)
                .stream()
                .collect(Collectors.toMap(RunSuggestion::getId, RunSuggestion::getStatus));

        List<CoverageScorer.Requirement> scored = keywords.stream()
                .map(keyword -> new CoverageScorer.Requirement(
                        keyword.getImportance(),
                        keyword.isCovered()
                                || statuses.get(keyword.getSuggestionId()) == SuggestionStatus.ACCEPTED))
                .toList();
        run.setMatchScore(CoverageScorer.score(scored));
        runRepository.save(run);
    }

    private RunSuggestion persistAddition(UUID runId, GapAnalysisResult.RequirementVerdict verdict, int ordinal) {
        GapAnalysisResult.ProposedAddition addition = verdict.addition();
        RunSuggestion suggestion = new RunSuggestion();
        suggestion.setRunId(runId);
        suggestion.setKeyword(verdict.requirement().keyword());
        suggestion.setKind(addition.kind());
        suggestion.setTargetSection(addition.section());
        suggestion.setContent(addition.label());
        suggestion.setAnchor(addition.anchor());
        suggestion.setPlacement(addition.placement());
        suggestion.setInsertText(addition.insert());
        // Always PROPOSED: putting something in the resume is the user's decision.
        suggestion.setStatus(SuggestionStatus.PROPOSED);
        suggestion.setOrdinal(ordinal);
        return suggestionRepository.save(suggestion);
    }

}
