package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumetailor.jd.JdAnalysis;
import com.resumetailor.jd.JobDescription;
import com.resumetailor.jd.JobDescriptionService;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.llm.LlmChatResult;
import com.resumetailor.llm.LlmSettings;
import com.resumetailor.llm.OpenAiCompatibleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM call 1: turns a raw job description into structured requirements.
 *
 * <p>Results are cached per job description. Re-tailoring the same JD against a
 * different resume, or re-running after an edit, should not pay for this call twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdAnalyzer {

    private final OpenAiCompatibleClient client;
    private final LlmResponseParser parser;
    private final JobDescriptionService jobDescriptionService;
    private final ObjectMapper objectMapper;

    public JdAnalysisResult analyze(JobDescription jd, LlmSettings settings) {
        return jobDescriptionService.findAnalysis(jd.getId())
                // A cached extraction from an older prompt is not reusable: the current
                // prompt rejects the qualifiers and non-skills the old one let through.
                .filter(cached -> cached.getPromptVersion() >= Prompts.JD_ANALYSIS_VERSION)
                .map(this::fromCache)
                .orElseGet(() -> callAndCache(jd, settings));
    }

    private JdAnalysisResult callAndCache(JobDescription jd, LlmSettings settings) {
        log.debug("Analyzing job description {}", jd.getId());
        LlmChatResult result = client.chat(
                settings, Prompts.JD_ANALYSIS_SYSTEM, Prompts.jdAnalysisUser(jd), true);
        JdAnalysisResult analysis = parser.parseJdAnalysis(result.content(), result.model());

        // One analysis per job description, so a re-extraction updates the existing row.
        JdAnalysis entity = jobDescriptionService.findAnalysis(jd.getId()).orElseGet(JdAnalysis::new);
        entity.setJobDescriptionId(jd.getId());
        entity.setPromptVersion(Prompts.JD_ANALYSIS_VERSION);
        entity.setModelUsed(analysis.modelUsed());
        entity.setCompany(analysis.company());
        entity.setRole(analysis.role());
        entity.setKeywords(writeKeywords(analysis.keywords()));
        entity.setResponsibilities(writeStrings(analysis.responsibilities()));
        entity.setMustHaves(writeStrings(analysis.mustHaves()));
        jobDescriptionService.saveAnalysis(entity);

        // Fill in company/role on the JD when the user left them blank.
        jobDescriptionService.updateLabels(jd.getId(), analysis.company(), analysis.role());
        return analysis;
    }

    private JdAnalysisResult fromCache(JdAnalysis entity) {
        return new JdAnalysisResult(
                entity.getCompany(),
                entity.getRole(),
                readKeywords(entity.getKeywords()),
                readStrings(entity.getResponsibilities()),
                readStrings(entity.getMustHaves()),
                entity.getModelUsed());
    }

    private String writeKeywords(List<JdKeyword> keywords) {
        ArrayNode array = objectMapper.createArrayNode();
        for (JdKeyword keyword : keywords) {
            ObjectNode node = array.addObject();
            node.put("keyword", keyword.keyword());
            node.put("importance", keyword.importance().name());
        }
        return array.toString();
    }

    private List<JdKeyword> readKeywords(String json) {
        try {
            List<JdKeyword> keywords = new ArrayList<>();
            for (var node : objectMapper.readTree(json)) {
                keywords.add(new JdKeyword(
                        node.path("keyword").asText(),
                        com.resumetailor.keyword.KeywordImportance.parse(node.path("importance").asText())));
            }
            return keywords;
        } catch (Exception ex) {
            log.warn("Could not read cached JD keywords, treating as empty", ex);
            return List.of();
        }
    }

    private String writeStrings(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array.toString();
    }

    private List<String> readStrings(String json) {
        try {
            List<String> values = new ArrayList<>();
            objectMapper.readTree(json).forEach(node -> values.add(node.asText()));
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
