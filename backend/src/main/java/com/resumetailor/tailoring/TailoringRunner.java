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

/**
 * The tailoring pipeline, run off the request thread.
 *
 * <p>Four model calls: read the posting, rewrite the resume, write the summary at several
 * lengths, then check the resume against the posting's requirements. The last two each have
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

        SummaryContext summaryContext;
        JdAnalysisResult analysis;
        LlmSettings settings;
        try {
            settings = llmProfileService.resolveSettings(context.llmProfileId());
            JobDescription jd = jobDescriptionService.require(context.jobDescriptionId());
            analysis = jdAnalyzer.analyze(jd, settings);

            LlmChatResult completion = client.chat(
                    settings,
                    Prompts.tailoringSystem(context.format()),
                    Prompts.tailoringUser(analysis, context.originalBody()),
                    true);
            TailorOutput output = parser.parseTailorOutput(completion.content());

            summaryContext = store.saveRewrite(
                    runId, output, completion.model(),
                    completion.promptTokens(), completion.completionTokens());
            log.info("Run {} rewrite saved", runId);
        } catch (Exception ex) {
            log.warn("Run {} failed: {}", runId, ex.getMessage());
            store.markFailed(runId, ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return;
        }

        // Before the gap check, so its anchors come from the summary the user will be looking at.
        writeSummary(summaryContext, analysis, settings);
        checkGaps(store.gapContext(runId), analysis, settings);
    }

    /** Re-runs only the summary options, from the model's pristine rewrite. */
    @Async(AsyncConfig.TAILORING_EXECUTOR)
    public void regenerateSummary(UUID runId) {
        SummaryContext context;
        try {
            context = store.beginSummary(runId);
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
        try {
            store.saveSummary(context.runId(), summaryWriter.write(context, analysis, settings));
            log.info("Run {} summary options written", context.runId());
        } catch (Exception ex) {
            log.warn("Summary options for run {} failed: {}", context.runId(), ex.getMessage());
            store.markSummaryFailed(context.runId(), message(ex));
        }
    }

    /**
     * Failures here are contained: the rewrite is already saved and usable, so a bad gap
     * check records its own error for the user to retry rather than failing the run.
     */
    private void checkGaps(GapContext context, JdAnalysisResult analysis, LlmSettings settings) {
        try {
            // Skills already explained for anyone's earlier posting are not asked for again.
            Map<String, String> known = skillDefinitions.descriptionsFor(
                    analysis.keywords().stream().map(JdKeyword::keyword).toList());
            List<JdKeyword> needDescription = analysis.keywords().stream()
                    .filter(k -> !known.containsKey(KeywordNormalizer.normalize(k.keyword())))
                    .toList();

            GapAnalysisResult result = gapAnalyzer.analyze(
                    analysis.keywords(), needDescription, context.originalBody(), context.body(),
                    context.format(), context.alreadyAdded(), settings);
            store.saveGaps(context.runId(), result);
            rememberDescriptions(result);
            log.info("Run {} gap analysis complete", context.runId());
        } catch (Exception ex) {
            log.warn("Gap analysis for run {} failed: {}", context.runId(), ex.getMessage());
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

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.toString() : ex.getMessage();
    }
}
