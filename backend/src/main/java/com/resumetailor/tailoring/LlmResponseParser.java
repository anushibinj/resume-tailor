package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.llm.JsonExtractor;
import com.resumetailor.llm.LlmException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns raw model output into typed results.
 *
 * <p>Deliberately forgiving about shape -- missing optional arrays, extra fields and
 * unexpected enum spellings are tolerated -- but strict about the one field a run
 * cannot proceed without: {@code tailoredBody}.
 */
@Component
@RequiredArgsConstructor
public class LlmResponseParser {

    /** Guards against a model that echoes one bullet instead of the whole body. */
    private static final int MIN_TAILORED_BODY_CHARS = 40;

    private final ObjectMapper objectMapper;

    public JdAnalysisResult parseJdAnalysis(String raw, String modelUsed) {
        JsonNode root = readJson(raw);

        List<JdKeyword> keywords = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : root.path("keywords")) {
            // Accept both {"keyword": "...", "importance": "..."} and a bare string.
            String keyword = node.isTextual() ? node.asText() : node.path("keyword").asText("");
            if (keyword.isBlank() || !seen.add(keyword.toLowerCase().trim())) {
                continue;
            }
            keywords.add(new JdKeyword(
                    keyword.trim(),
                    KeywordImportance.parse(node.path("importance").asText(null))));
        }

        return new JdAnalysisResult(
                textOrNull(root, "company"),
                textOrNull(root, "role"),
                keywords,
                stringList(root.path("responsibilities")),
                stringList(root.path("mustHaves")),
                modelUsed);
    }

    public TailorOutput parseTailorOutput(String raw) {
        JsonNode root = readJson(raw);

        String body = root.path("tailoredBody").asText("");
        if (body.isBlank()) {
            throw new LlmException(
                    "The model did not return a 'tailoredBody' field. Try a more capable model, "
                            + "or lower the temperature in Settings.");
        }
        if (body.length() < MIN_TAILORED_BODY_CHARS) {
            throw new LlmException(
                    "The model returned a suspiciously short resume body (" + body.length()
                            + " characters). It likely returned a fragment instead of the whole resume.");
        }

        List<TailorOutput.ChangeItem> changes = new ArrayList<>();
        for (JsonNode node : root.path("changes")) {
            String section = node.path("section").asText("");
            changes.add(new TailorOutput.ChangeItem(
                    section.isBlank() ? null : section.trim(),
                    normalizeChangeType(node.path("changeType").asText(null)),
                    textOrNull(node, "rationale")));
        }

        List<TailorOutput.SuggestionItem> suggestions = new ArrayList<>();
        for (JsonNode node : root.path("suggestions")) {
            String content = node.path("content").asText("");
            if (content.isBlank()) {
                continue;
            }
            suggestions.add(new TailorOutput.SuggestionItem(
                    SuggestionKind.parse(node.path("kind").asText(null)),
                    textOrNull(node, "targetSection"),
                    content.trim(),
                    textOrNull(node, "rationale")));
        }

        return new TailorOutput(body, changes, suggestions);
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(JsonExtractor.extractObject(raw));
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException("The model returned malformed JSON: " + ex.getMessage(), ex);
        }
    }

    private static String normalizeChangeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "REWORDED";
        }
        return switch (raw.trim().toUpperCase()) {
            case "REORDERED" -> "REORDERED";
            case "TRIMMED" -> "TRIMMED";
            case "EMPHASISED", "EMPHASIZED" -> "EMPHASISED";
            default -> "REWORDED";
        };
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() || "null".equals(value) ? null : value.trim();
    }

    private static List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            String value = node.isTextual() ? node.asText() : node.toString();
            if (!value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }
}
