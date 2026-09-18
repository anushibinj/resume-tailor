package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.keyword.KeywordNormalizer;
import com.resumetailor.llm.JsonExtractor;
import com.resumetailor.llm.LlmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns raw model output into typed results.
 *
 * <p>Deliberately forgiving about shape -- missing optional arrays, extra fields and
 * unexpected enum spellings are tolerated -- but strict about the one field a run
 * cannot proceed without: {@code tailoredBody}.
 */
@Slf4j
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

        return new TailorOutput(body, changes);
    }

    /**
     * Reads the gap analysis, guaranteeing one verdict per requirement in the order asked.
     *
     * <p>A model that drops a requirement, or marks one uncovered without saying how to add
     * it, must not leave a gap the user cannot act on -- those fall back to a plain
     * section-placed addition named after the requirement itself.
     */
    public GapAnalysisResult parseGapAnalysis(String raw, List<JdKeyword> requirements,
                                              Set<UUID> knownAdditionIds, String modelUsed) {
        JsonNode root = readJson(raw);

        Map<String, JsonNode> byKeyword = new HashMap<>();
        for (JsonNode node : verdictArray(root)) {
            String keyword = node.path("keyword").asText("");
            if (!keyword.isBlank()) {
                byKeyword.putIfAbsent(normalizeKeyword(keyword), node);
            }
        }

        // A reply that matches few or none of the requirements is not an analysis, and
        // filling the gaps in from defaults would present fabricated verdicts as the
        // model's own -- every requirement listed as missing, each with a bare "add the
        // keyword" suggestion. Fail instead, so the user sees why and can retry.
        long matched = requirements.stream()
                .filter(r -> byKeyword.containsKey(normalizeKeyword(r.keyword())))
                .count();
        if (requirements.size() >= 2 && matched * 2 < requirements.size()) {
            log.warn("Gap analysis matched {} of {} requirements; raw reply began: {}",
                    matched, requirements.size(), preview(raw));
            throw new LlmException(
                    "The model answered for only " + matched + " of " + requirements.size()
                            + " requirements, so the check was discarded rather than reported as gaps. "
                            + "Its reply was probably cut short or in an unexpected shape -- check "
                            + "Max output tokens in Settings, then run the check again.");
        }

        List<GapAnalysisResult.RequirementVerdict> verdicts = new ArrayList<>();
        for (JdKeyword requirement : requirements) {
            JsonNode node = byKeyword.get(normalizeKeyword(requirement.keyword()));
            if (node == null) {
                verdicts.add(fallbackVerdict(requirement));
                continue;
            }

            String description = textOrNull(node, "description");
            boolean covered = node.path("covered").asBoolean(false);
            if (covered) {
                verdicts.add(new GapAnalysisResult.RequirementVerdict(
                        requirement, true, textOrNull(node, "evidence"), null, null, description));
                continue;
            }

            UUID addressedBy = parseUuid(textOrNull(node, "addressedBy"));
            if (addressedBy != null && knownAdditionIds.contains(addressedBy)) {
                verdicts.add(new GapAnalysisResult.RequirementVerdict(
                        requirement, false, null, addressedBy, null, description));
                continue;
            }

            GapAnalysisResult.ProposedAddition addition = parseAddition(node.path("addition"), requirement);
            verdicts.add(new GapAnalysisResult.RequirementVerdict(
                    requirement, false, null, null, addition, description));
        }
        return new GapAnalysisResult(verdicts, modelUsed);
    }

    private GapAnalysisResult.ProposedAddition parseAddition(JsonNode node, JdKeyword requirement) {
        String label = node.path("label").asText("");
        String insert = node.path("insert").asText("");
        if (label.isBlank() && insert.isBlank()) {
            return defaultAddition(requirement);
        }
        String anchor = textOrNull(node, "anchor");
        return new GapAnalysisResult.ProposedAddition(
                label.isBlank() ? requirement.keyword() : label.trim(),
                SuggestionKind.parse(node.path("kind").asText(null)),
                textOrNull(node, "section"),
                anchor,
                // Placement only means something with an anchor to place against.
                anchor == null ? null : Placement.parse(node.path("placement").asText(null)),
                insert.isBlank() ? null : insert);
    }

    private GapAnalysisResult.RequirementVerdict fallbackVerdict(JdKeyword requirement) {
        return new GapAnalysisResult.RequirementVerdict(
                requirement, false, null, null, defaultAddition(requirement), null);
    }

    /** Last resort: offer the requirement itself as a skill, placed by section. */
    private GapAnalysisResult.ProposedAddition defaultAddition(JdKeyword requirement) {
        return new GapAnalysisResult.ProposedAddition(
                requirement.keyword(), SuggestionKind.SKILL, "Skills", null, null, null);
    }

    /**
     * Finds the array of per-requirement verdicts. Models usually return
     * {@code {"requirements": [...]}} but sometimes name the field something else, so
     * fall back to the first array of objects carrying a "keyword". (A bare top-level
     * array cannot arrive here: JSON mode returns an object, and JsonExtractor pulls out
     * an object.)
     */
    private static JsonNode verdictArray(JsonNode root) {
        JsonNode named = root.path("requirements");
        if (named.isArray()) {
            return named;
        }
        for (JsonNode candidate : root) {
            if (candidate.isArray() && candidate.size() > 0 && candidate.get(0).has("keyword")) {
                return candidate;
            }
        }
        return root.path("requirements");
    }

    private static String preview(String raw) {
        String text = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    /** Matching key for a requirement, applied to both sides. See {@link KeywordNormalizer}. */
    private static String normalizeKeyword(String keyword) {
        return KeywordNormalizer.normalize(keyword);
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
