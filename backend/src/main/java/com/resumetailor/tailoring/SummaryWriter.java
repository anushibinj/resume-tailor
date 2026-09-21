package com.resumetailor.tailoring;

import com.resumetailor.config.SummaryProperties;
import com.resumetailor.llm.LlmChatResult;
import com.resumetailor.llm.LlmSettings;
import com.resumetailor.llm.OpenAiCompatibleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM call 4: writes the rewrite's summary at each length the run page's slider offers, so
 * choosing one is instant instead of waiting on a model.
 *
 * <p>Deliberately its own call rather than a field on the tailoring call. The rewrite is
 * prompted to keep the resume's length; asking it for four more paragraphs as well
 * makes that prompt harder to follow and would leave older runs, and any run whose options
 * failed, with no way to get them short of redoing the whole rewrite.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryWriter {

    private final OpenAiCompatibleClient client;
    private final LlmResponseParser parser;
    private final SummaryProperties properties;

    public SummaryOptions write(SummaryContext context, JdAnalysisResult analysis, LlmSettings settings) {
        log.debug("Writing summary options for run {}", context.runId());

        LlmChatResult result = client.chat(
                settings,
                Prompts.summaryVariantsSystem(context.format(), properties.charsPerLine()),
                Prompts.summaryVariantsUser(analysis, context.originalBody(), context.tailoredBody()),
                true);
        log.debug("Summary options raw reply: {}", preview(result.content()));

        return parser.parseSummaryOptions(result.content(), context.tailoredBody(), context.format(), result.model());
    }

    private static String preview(String content) {
        String text = content == null ? "" : content.strip();
        return text.length() <= 1500 ? text : text.substring(0, 1500) + "… (truncated in log)";
    }
}
