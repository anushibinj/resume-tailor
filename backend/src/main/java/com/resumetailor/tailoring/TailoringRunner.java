package com.resumetailor.tailoring;

import com.resumetailor.config.AsyncConfig;
import com.resumetailor.jd.JobDescription;
import com.resumetailor.jd.JobDescriptionService;
import com.resumetailor.llm.LlmChatResult;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.llm.LlmSettings;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.keyword.KeywordNormalizer;
import com.resumetailor.llm.OpenAiCompatibleClient;
import com.resumetailor.skill.SkillDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tailoring pipeline, run off the request thread.
 *
 * <p>Four model calls: read the posting, rewrite the resume, write the summary at several
 * lengths, then check the resume against the posting's requirements. The last two run
 * concurrently and each have
 * their own status -- the rewrite is the expensive part, so it is saved and shown as soon as
 * it lands, and a failed summary or check can be retried without redoing it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TailoringRunner {

    private final TailoringRunStore store;
    private final JobDescriptionService jobDescriptionService;
    private final LlmProfileService llmProfileService;
    private final JdAnalyzer jdAnalyzer;
    private final GapAnalyzer gapAnalyzer;
    private final SummaryWriter summaryWriter;
    private final SkillDefinitionService skillDefinitions;
    private final OpenAiCompatibleClient client;
    private final LlmResponseParser parser;

    @Async(AsyncConfig.TAILORING_EXECUTOR)
    public void run(UUID runId) {
        RunContext context;
        try {
            context = store.markRunning(runId);
        } catch (RuntimeException ex) {
            log.error("Could not start run {}", runId, ex);
            return;
        }

        long started = System.nanoTime();
        log.info("Run {} started (format={}, resumeChars={})",
                runId, context.format(), context.originalBody().length());

        SummaryContext summaryContext;
        JdAnalysisResult analysis;
        LlmSettings settings;
        try {
            settings = llmProfileService.resolveSettings(context.llmProfileId());
            log.debug("Run {} using model {} at {}", runId, settings.model(), settings.baseUrl());
            JobDescription jd = jobDescriptionService.require(context.jobDescriptionId());
            log.info("Run {} step 1/4: analysing job description", runId);
            long step = System.nanoTime();
            analysis = jdAnalyzer.analyze(jd, settings);
            log.info("Run {} step 1/4 done in {} ms ({} requirements)",
                    runId, since(step), analysis.keywords().size());

            log.info("Run {} step 2/4: rewriting resume", runId);
            step = System.nanoTime();

            LlmChatResult completion = client.chat(
                    settings,
                    Prompts.tailoringSystem(context.format()),
                    Prompts.tailoringUser(analysis, context.originalBody()),
                    true);
            log.debug("Run {} rewrite reply received in {} ms; parsing", runId, since(step));
            TailorOutput output = parser.parseTailorOutput(completion.content());

            summaryContext = store.saveRewrite(
                    runId, output, completion.model(),
                    completion.promptTokens(), completion.completionTokens());
            log.info("Run {} step 2/4 done in {} ms; rewrite saved (run COMPLETED, {} ms since start)",
                    runId, since(step), since(started));
        } catch (Exception ex) {
            log.warn("Run {} failed after {} ms: {}", runId, since(started), ex.getMessage());
            store.markFailed(runId, ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return;
        }

        // The summary options and the gap check both read only the saved rewrite, so they run
        // side by side instead of one after the other. The gap check therefore sees the rewrite
        // before a chosen summary length is swapped in; an accepted addition anchored inside
        // the summary falls back to section placement (see EffectiveBody) rather than failing.
        GapContext gapContext = store.gapContext(runId);
        ExecutorService summaryThread = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> summary = CompletableFuture.runAsync(
                    () -> writeSummary(summaryContext, analysis, settings), summaryThread);
            checkGaps(gapContext, analysis, settings);
            summary.join();
        } finally {
            summaryThread.shutdown();
        }
        log.info("Run {} pipeline finished in {} ms", runId, since(started));
    }

    /** Re-runs only the summary options, from the model's pristine rewrite. */
    @Async(AsyncConfig.TAILORING_EXECUTOR)
    public void regenerateSummary(UUID runId) {
        SummaryContext context;
        try {
            context = store.beginSummary(runId);
            log.info("Run {} summary options re-requested", runId);
        } catch (RuntimeException ex) {
            log.error("Could not start summary options for run {}", runId, ex);
            return;
        }

        try {
            LlmSettings settings = llmProfileService.resolveSettings(context.llmProfileId());
            JobDescription jd = jobDescriptionService.require(context.jobDescriptionId());
            writeSummary(context, jdAnalyzer.analyze(jd, settings), settings);
        } catch (Exception ex) {
            log.warn("Summary options for run {} failed: {}", runId, ex.getMessage());
            store.markSummaryFailed(runId, message(ex));
        }
    }

    /** Re-runs only the gap analysis, against the document as it currently stands. */
    @Async(AsyncConfig.TAILORING_EXECUTOR)
    public void analyzeGaps(UUID runId) {
        GapContext context;
        try {
            context = store.beginGapAnalysis(runId);
            log.info("Run {} gap analysis re-requested", runId);
        } catch (RuntimeException ex) {
            log.error("Could not start gap analysis for run {}", runId, ex);
            return;
        }

        try {
            LlmSettings settings = llmProfileService.resolveSettings(context.llmProfileId());
            JobDescription jd = jobDescriptionService.require(context.jobDescriptionId());
            checkGaps(context, jdAnalyzer.analyze(jd, settings), settings);
        } catch (Exception ex) {
            log.warn("Gap analysis for run {} failed: {}", runId, ex.getMessage());
            store.markGapsFailed(runId, message(ex));
        }
    }

    /**
     * Contained like the gap check: the rewrite is already saved and usable, so failing to
     * write the options records an error the user can retry from, and the pipeline goes on.
     */
    private void writeSummary(SummaryContext context, JdAnalysisResult analysis, LlmSettings settings) {
        log.info("Run {} step 3/4: writing summary options", context.runId());
        long step = System.nanoTime();
        try {
            store.saveSummary(context.runId(), summaryWriter.write(context, analysis, settings));
            log.info("Run {} step 3/4 done in {} ms; summary options written", context.runId(), since(step));
        } catch (Exception ex) {
            log.warn("Summary options for run {} failed after {} ms: {}",
                    context.runId(), since(step), ex.getMessage());
            store.markSummaryFailed(context.runId(), message(ex));
        }
    }

    /**
     * Failures here are contained: the rewrite is already saved and usable, so a bad gap
     * check records its own error for the user to retry rather than failing the run.
     */
    private void checkGaps(GapContext context, JdAnalysisResult analysis, LlmSettings settings) {
        log.info("Run {} step 4/4: checking gaps", context.runId());
        long step = System.nanoTime();
        try {
            // Skills already explained for anyone's earlier posting are not asked for again.
            Map<String, String> known = skillDefinitions.descriptionsFor(
                    analysis.keywords().stream().map(JdKeyword::keyword).toList());
            List<JdKeyword> needDescription = analysis.keywords().stream()
                    .filter(k -> !known.containsKey(KeywordNormalizer.normalize(k.keyword())))
                    .toList();
            log.debug("Run {} gap check: {} requirements, {} already in the glossary, {} to describe, {} additions accepted",
                    context.runId(), analysis.keywords().size(), known.size(), needDescription.size(),
                    context.alreadyAdded().size());

            GapAnalysisResult result = gapAnalyzer.analyze(
                    analysis.keywords(), needDescription, context.originalBody(), context.body(),
                    context.format(), context.alreadyAdded(), settings);
            store.saveGaps(context.runId(), result);
            rememberDescriptions(result);
            log.info("Run {} step 4/4 done in {} ms; gap analysis complete ({} verdicts)",
                    context.runId(), since(step), result.verdicts().size());
        } catch (Exception ex) {
            log.warn("Gap analysis for run {} failed after {} ms: {}",
                    context.runId(), since(step), ex.getMessage());
            store.markGapsFailed(context.runId(), message(ex));
        }
    }

    /** A glossary write that fails must not cost the user a gap check that already succeeded. */
    private void rememberDescriptions(GapAnalysisResult result) {
        try {
            skillDefinitions.remember(
                    result.verdicts().stream()
                            .filter(v -> v.description() != null)
                            .map(v -> new SkillDefinitionService.Learned(v.requirement().keyword(), v.description()))
                            .toList(),
                    result.modelUsed());
        } catch (Exception ex) {
            log.warn("Could not save skill descriptions: {}", ex.getMessage());
        }
    }

    private static long since(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.toString() : ex.getMessage();
    }
}
