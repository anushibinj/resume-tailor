package com.resumetailor.tailoring;

import com.resumetailor.config.AsyncConfig;
import com.resumetailor.jd.JobDescription;
import com.resumetailor.jd.JobDescriptionService;
import com.resumetailor.keyword.KeywordMatch;
import com.resumetailor.keyword.KeywordMatcher;
import com.resumetailor.llm.LlmChatResult;
import com.resumetailor.llm.LlmSettings;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.llm.OpenAiCompatibleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The tailoring pipeline, run off the request thread.
 *
 * <p>Order matters: analyse the JD, rewrite the body, then score keywords in Java
 * against what the model actually produced.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TailoringRunner {

    private final TailoringRunStore store;
    private final JobDescriptionService jobDescriptionService;
    private final LlmProfileService llmProfileService;
    private final JdAnalyzer jdAnalyzer;
    private final OpenAiCompatibleClient client;
    private final LlmResponseParser parser;
    private final KeywordMatcher keywordMatcher;

    @Async(AsyncConfig.TAILORING_EXECUTOR)
    public void run(UUID runId) {
        RunContext context;
        try {
            context = store.markRunning(runId);
        } catch (RuntimeException ex) {
            log.error("Could not start run {}", runId, ex);
            return;
        }

        try {
            LlmSettings settings = llmProfileService.resolveSettings(context.llmProfileId());
            JobDescription jd = jobDescriptionService.require(context.jobDescriptionId());

            JdAnalysisResult analysis = jdAnalyzer.analyze(jd, settings);

            LlmChatResult completion = client.chat(
                    settings,
                    Prompts.tailoringSystem(context.format()),
                    Prompts.tailoringUser(analysis, context.originalBody()),
                    true);
            TailorOutput output = parser.parseTailorOutput(completion.content());

            List<KeywordMatch> matches = keywordMatcher.match(
                    analysis.keywords(), context.originalBody(), output.tailoredBody());
            int score = keywordMatcher.score(matches);

            store.saveSuccess(
                    runId, output, completion.model(),
                    completion.promptTokens(), completion.completionTokens(), matches, score);
            log.info("Run {} completed with match score {}", runId, score);
        } catch (Exception ex) {
            log.warn("Run {} failed: {}", runId, ex.getMessage());
            store.markFailed(runId, ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }
}
