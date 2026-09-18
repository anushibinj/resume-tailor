package com.resumetailor.tailoring;

import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.llm.LlmChatResult;
import com.resumetailor.llm.LlmSettings;
import com.resumetailor.llm.OpenAiCompatibleClient;
import com.resumetailor.resume.ResumeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LLM call 3: judges which of the posting's requirements the candidate's resume covers, and
 * writes the exact edit that would add each one it does not. Coverage is judged on the
 * original resume, not the rewrite, so the rewrite cannot vouch for itself.
 *
 * <p>This replaced a deterministic keyword matcher. The matcher compared strings, so a
 * requirement the model had phrased as "Java (Programming Language)" never matched a
 * resume that says "Java" throughout, and a Java developer's resume was reported as
 * missing Java. Coverage is a judgment about meaning, which is what a model is for.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GapAnalyzer {

    private final OpenAiCompatibleClient client;
    private final LlmResponseParser parser;

    /**
     * @param needDescription requirements missing from the shared skill glossary; the model
     *                        explains just these, alongside the coverage judgment
     */
    public GapAnalysisResult analyze(List<JdKeyword> requirements, List<JdKeyword> needDescription,
                                     String originalBody, String tailoredBody, ResumeFormat format,
                                     List<Prompts.ExistingAddition> alreadyAdded, LlmSettings settings) {
        if (requirements.isEmpty()) {
            return new GapAnalysisResult(List.of(), null);
        }
        log.debug("Checking {} requirements against the resume", requirements.size());

        LlmChatResult result = client.chat(
                settings,
                Prompts.gapAnalysisSystem(format),
                Prompts.gapAnalysisUser(requirements, needDescription, originalBody, tailoredBody, alreadyAdded),
                true);

        // The shape of this reply is the most common thing to go wrong with a new model,
        // and it is invisible without seeing it. Logged at DEBUG (on in the dev profile).
        log.debug("Gap analysis raw reply: {}", preview(result.content()));

        Set<UUID> knownAdditionIds = alreadyAdded.stream()
                .map(addition -> UUID.fromString(addition.id()))
                .collect(Collectors.toSet());
        return parser.parseGapAnalysis(result.content(), requirements, knownAdditionIds, result.model());
    }

    private static String preview(String content) {
        String text = content == null ? "" : content.strip();
        return text.length() <= 1500 ? text : text.substring(0, 1500) + "… (truncated in log)";
    }
}
