package com.resumetailor.tailoring;

import com.resumetailor.keyword.KeywordMatch;
import com.resumetailor.resume.ResumeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Transactional
    public void saveSuccess(UUID runId, TailorOutput output, String modelUsed,
                            Integer promptTokens, Integer completionTokens,
                            List<KeywordMatch> matches, int score) {
        TailoringRun run = runRepository.findById(runId).orElseThrow();

        run.setTailoredBody(output.tailoredBody());
        // The preamble is reattached here, never regenerated -- it was never sent to the model.
        run.setTailoredSource(ResumeParser.reassemble(
                run.getPreamble(), output.tailoredBody(), run.getDocumentTail()));
        run.setModelUsed(modelUsed);
        run.setPromptTokens(promptTokens);
        run.setCompletionTokens(completionTokens);
        run.setMatchScore(score);
        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        // A re-run of the same row replaces its children rather than accumulating them.
        changeRepository.deleteAllByRunId(runId);
        suggestionRepository.deleteAllByRunId(runId);
        keywordRepository.deleteAllByRunId(runId);

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

        ordinal = 0;
        for (TailorOutput.SuggestionItem item : output.suggestions()) {
            RunSuggestion suggestion = new RunSuggestion();
            suggestion.setRunId(runId);
            suggestion.setKind(item.kind());
            suggestion.setTargetSection(item.targetSection());
            suggestion.setContent(item.content());
            suggestion.setRationale(item.rationale());
            // Always PROPOSED: acceptance is the user's assertion, not the model's.
            suggestion.setStatus(SuggestionStatus.PROPOSED);
            suggestion.setOrdinal(ordinal++);
            suggestionRepository.save(suggestion);
        }

        ordinal = 0;
        for (KeywordMatch match : matches) {
            RunKeyword keyword = new RunKeyword();
            keyword.setRunId(runId);
            keyword.setKeyword(match.keyword());
            keyword.setImportance(match.importance());
            keyword.setPresentInOriginal(match.presentInOriginal());
            keyword.setPresentInTailored(match.presentInTailored());
            keyword.setOrdinal(ordinal++);
            keywordRepository.save(keyword);
        }
    }
}
